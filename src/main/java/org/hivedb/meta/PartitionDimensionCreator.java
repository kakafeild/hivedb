package org.hivedb.meta;

import java.util.Collection;

import org.hivedb.Hive;
import org.hivedb.configuration.EntityConfig;
import org.hivedb.configuration.EntityHiveConfig;
import org.hivedb.configuration.EntityIndexConfig;
import org.hivedb.util.ReflectionTools;
import org.hivedb.util.database.JdbcTypeMapper;
import org.hivedb.util.functional.Atom;
import org.hivedb.util.functional.Transform;
import org.hivedb.util.functional.Unary;

public class PartitionDimensionCreator {

	public static PartitionDimension create(final EntityHiveConfig entityHiveConfig, Hive hive) {
		EntityConfig firstEntityConfig = Atom.getFirstOrThrow(entityHiveConfig.getEntityConfigs());
		String partitionDimensionName = firstEntityConfig.getPartitionDimensionName();
				
		PartitionDimension dimension = new PartitionDimension(
			partitionDimensionName,
			JdbcTypeMapper.primitiveTypeToJdbcType(
					ReflectionTools.getPropertyType(
							firstEntityConfig.getRepresentedInterface(), 
							firstEntityConfig.getPrimaryIndexKeyPropertyName())),
			cloneResources(
				Transform.map(new Unary<EntityConfig, Resource>() {
					public Resource f(EntityConfig entityConfig) {
						return createResource(entityConfig);
					}
				}, entityHiveConfig.getEntityConfigs()))); // clone because resources are given the new partition dimension id
	
		dimension.updateId(hive.getPartitionDimension().getId());
		return dimension;
	}
	private static Resource createResource(final EntityConfig entityConfig) {
		Resource resource = new Resource(
				entityConfig.getResourceName(), 
				JdbcTypeMapper.primitiveTypeToJdbcType(
						ReflectionTools.getPropertyType(
								entityConfig.getRepresentedInterface(), 
								entityConfig.getIdPropertyName())),
				entityConfig.isPartitioningResource(),
				constructSecondaryIndexesOfResource(entityConfig));
		resource.updateId(1);
		return resource;
	}
	
	private static Collection<Resource> cloneResources(Collection<Resource> resources) {
		return Transform.map(new Unary<Resource,Resource>() {
			public Resource f(Resource resource) {
				return new Resource(resource.getId(), resource.getName(), resource.getColumnType(), resource.isPartitioningResource(), resource.getSecondaryIndexes());
			}
		}, resources);
	}

	public static Collection<SecondaryIndex> constructSecondaryIndexesOfResource(final EntityConfig entityConfig) {	
		try {
			return 
				Transform.map(
					new Unary<EntityIndexConfig, SecondaryIndex>() {
						public SecondaryIndex f(EntityIndexConfig secondaryIndexIdentifiable) {
							try {
								return new SecondaryIndex(
									secondaryIndexIdentifiable.getIndexName(),
									JdbcTypeMapper.primitiveTypeToJdbcType(
										secondaryIndexIdentifiable.getIndexClass()));											
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
					}}, 
					entityConfig.getEntitySecondaryIndexConfigs());
					
		} catch (Exception e) {
			throw new RuntimeException(e);
		}		
	}
}
