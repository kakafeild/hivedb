package org.hivedb.hibernate;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.hivedb.configuration.EntityHiveConfig;
import org.hivedb.util.GenerateInstance;
import org.hivedb.util.GeneratedInstanceInterceptor;
import org.hivedb.util.ReflectionTools;
import org.hivedb.util.database.test.H2HiveTestCase;
import org.hivedb.util.database.test.MySqlHiveTestCase;
import org.hivedb.util.database.test.WeatherReport;
import org.hivedb.util.functional.Amass;
import org.hivedb.util.functional.Atom;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Generate;
import org.hivedb.util.functional.Generator;
import org.hivedb.util.functional.Joiner;
import org.hivedb.util.functional.NumberIterator;
import org.hivedb.util.functional.Transform;
import org.hivedb.util.functional.Unary;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BaseDataAccessObjectTest extends H2HiveTestCase {
	private EntityHiveConfig config;

	private static Random random = new Random();
	@BeforeMethod
	public void setup() throws Exception {
		this.cleanupAfterEachTest = true;
		this.config = getEntityHiveConfig();
	}
	
	@Test
	public void testGet() throws Exception {
		DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		WeatherReport original = getPersistentInstance(dao);
		WeatherReport report = dao.get(original.getReportId());
		assertEquals(original, report);
	}
	
	@Test
	public void testFindByProperty() throws Exception {
		DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		WeatherReport report = new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
		dao.save(report);
		int temperature = random.nextInt();
		GeneratedInstanceInterceptor.setProperty(report, "temperature", temperature);
		dao.save(report);
		WeatherReport found = Atom.getFirstOrThrow(dao.findByProperty("temperature", temperature));
		Assert.assertEquals(report, found, ReflectionTools.getDifferingFields(report, found, WeatherReport.class).toString());
		found = Atom.getFirstOrThrow(dao.findByProperty("regionCode", report.getRegionCode()));
		assertEquals(report, found);
		found = Atom.getFirstOrThrow(dao.findByProperty("weatherEvents", Atom.getFirstOrThrow(report.getWeatherEvents()).getEventId()));
		assertEquals(report, found);
		found = Atom.getFirstOrThrow(dao.findByProperty("continent", report.getContinent()));
		assertEquals(report, found);	
		found = Atom.getFirstOrThrow(dao.findByProperty("sources", Atom.getFirstOrThrow(report.getSources())));
		assertEquals(report, found);
	}
	
	@Test
	public void testFindByPropertyPaged() throws Exception {
		final DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		Collection<WeatherReport> reports =
			Generate.create(new Generator<WeatherReport>() {
				public WeatherReport generate() {
					WeatherReport report =  new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
					GeneratedInstanceInterceptor.setProperty(report, "continent", "Derkaderkastan");
					GeneratedInstanceInterceptor.setProperty(report, "temperature", 101);
					return dao.save(report);
				}}, new NumberIterator(12));	
		
		Assert.assertEquals(dao.findByProperty("temperature", 101).size(), 12);
		final Collection<WeatherReport> flatten = Filter.grepUnique(Transform.flatten(Transform.map(new Unary<Integer, Collection<WeatherReport>>() {
			public Collection<WeatherReport> f(Integer i) {
				final Collection<WeatherReport> findByProperty = dao.findByProperty("temperature", 101 ,(i-1)*4, 4);
				return findByProperty;
			}
		}, new NumberIterator(3))));
		final HashSet<WeatherReport> retrievedSet = new HashSet<WeatherReport>(flatten);
	
		Assert.assertEquals(
				retrievedSet.size(),
				reports.size());
		
		Assert.assertEquals(
				retrievedSet.hashCode(),
				new HashSet(reports).hashCode());
	}
	

	@Test
	public void testFindByPropertyRange() throws Exception {
		
		Collection<WeatherReport> reports =
			Generate.create(new Generator<WeatherReport>() {
				public WeatherReport generate() {
					return new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
				}}, new NumberIterator(5));
		
		DataAccessObject<WeatherReport,Integer> dao = getDao(getGeneratedClass());
		dao.saveAll(reports);
		Collection<WeatherReport> x = dao.findByProperty("temperature", Atom.getFirst(reports).getTemperature());
		Integer min = Amass.min(
				new Unary<WeatherReport, Integer>() {
					public Integer f(WeatherReport weatherReport) {
						return weatherReport.getTemperature();
					}},
				reports,
				Integer.class);
		Integer max = Amass.max(
				new Unary<WeatherReport, Integer>() {
					public Integer f(WeatherReport weatherReport) {
						return weatherReport.getTemperature();
					}},
				reports,
				Integer.class);
		Collection<WeatherReport> range = dao.findByPropertyRange("temperature",  min, max);
		assertEquals(reports.size(), range.size());
		
		Collection<WeatherReport> smallerRange =
			dao.findByPropertyRange("temperature", Atom.getFirst(reports).getTemperature(), Atom.getFirst(reports).getTemperature());	
		assertEquals(1, smallerRange.size());
	}
	
	@Test
	public void testFindByPropertyRangePaged() throws Exception {
		final DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		final int INSTANCE_COUNT = 12;
		Collection<WeatherReport> set = new HashSet<WeatherReport>();
		int min=0, max=0;
		for (int i=0; i<INSTANCE_COUNT; i++) {
			int temperature = random.nextInt();
			min = Math.min(min, temperature);
			max = Math.max(max, temperature);
			WeatherReport report = new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
			GeneratedInstanceInterceptor.setProperty(report, "temperature", temperature);
			dao.save(report);
			set.add(report);
		}
		final int finalMin = min;
		final int finalMax = max;
		Assert.assertEquals(
				new HashSet<WeatherReport>(Transform.flatten(Transform.map(new Unary<Integer, Collection<WeatherReport>>() {
					public Collection<WeatherReport> f(Integer i) {
						final Collection<WeatherReport> value = dao.findByPropertyRange("temperature", finalMin, finalMax, (i-1)*4, 4);
						return value;
					}
				}, new NumberIterator(3)))).hashCode(),
				set.hashCode());
	}
		
	@Test
	public void testDelete() throws Exception {
		DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		WeatherReport original = getPersistentInstance(dao);
		dao.delete(original.getReportId());
		assertNull(dao.get(original.getReportId()));
	}
	
	@Test 
	public void testInsert() {			
		DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		WeatherReport report = new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
		dao.save(report);
		WeatherReport savedReport = dao.get(report.getReportId());
		assertNotNull(savedReport);
		assertEquals(report, savedReport);
	}

	private Class getGeneratedClass() {
		return GeneratedInstanceInterceptor.newInstance(WeatherReport.class).getClass();
	}

	private DataAccessObject<WeatherReport, Integer> getDao(Class clazz) {
		
		return new BaseDataAccessObjectFactory<WeatherReport,Integer>(
				this.config,
				getMappedClasses(),
				clazz, getHive()).create();
	}
	
	private<T> T getInstance(Class<T> clazz) throws Exception {
		return new GenerateInstance<T>(clazz).generate();
	}
	
	private WeatherReport getPersistentInstance(DataAccessObject<WeatherReport, Integer> dao) throws Exception {
		return dao.save(getInstance(WeatherReport.class));
	}
	
	@Test
	public void testUpdate() throws Exception {
		DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		WeatherReport original = getPersistentInstance(dao);
		WeatherReport updated = dao.get(original.getReportId());
		GeneratedInstanceInterceptor.setProperty(updated, "latitude", new Double(30));
		GeneratedInstanceInterceptor.setProperty(updated, "longitude", new Double(30));
		dao.save(updated);
		WeatherReport persisted = dao.get(updated.getReportId());
		assertEquals(updated, persisted);
		assertFalse(updated.equals(original));
	}

	@Test
	public void testSaveAll() throws Exception {
		Collection<WeatherReport> reports = new ArrayList<WeatherReport>();
		for(int i=0; i<5; i++) {
			WeatherReport report = new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
			GeneratedInstanceInterceptor.setProperty(report, "reportId", i);
			reports.add(report);
		}
		DataAccessObject<WeatherReport,Integer> dao = getDao(getGeneratedClass());
		dao.saveAll(reports);
		
		for(WeatherReport report : reports)
			assertEquals(report, dao.get(report.getReportId()));
	}
	
	@Test
	public void testUpdateAll() throws Exception {
		Collection<WeatherReport> reports = new ArrayList<WeatherReport>();
		for(int i=0; i<5; i++) {
			WeatherReport report = new GenerateInstance<WeatherReport>(WeatherReport.class).generate();
			GeneratedInstanceInterceptor.setProperty(report, "reportId", i);
			reports.add(report);
		}
		DataAccessObject<WeatherReport,Integer> dao = getDao(getGeneratedClass());
		dao.saveAll(reports);


		Collection<WeatherReport> updated = new ArrayList<WeatherReport>();
		for(WeatherReport report : reports){
			GeneratedInstanceInterceptor.setProperty(report, "temperature", 100);
			updated.add(report);
		}
		dao.saveAll(updated);	
		
		for(WeatherReport report : updated) {
			final WeatherReport weatherReport = dao.get(report.getReportId());
			assertEquals(report, weatherReport);
		}
	}
	
	@Test
	public void testExists() throws Exception {
		DataAccessObject<WeatherReport, Integer> dao = getDao(getGeneratedClass());
		assertFalse(dao.exists(88));
		WeatherReport original = getPersistentInstance(dao);
		assertTrue(dao.exists(original.getReportId()));
	}

}
