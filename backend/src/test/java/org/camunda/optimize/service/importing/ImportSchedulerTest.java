package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessInstanceImportService;
import org.camunda.optimize.service.importing.impl.VariableImportService;
import org.camunda.optimize.service.importing.job.schedule.IdBasedImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ImportSchedulerTest extends AbstractSchedulerTest {

  public static final String TEST_ID = "testId";
  private static final String TEST_ENGINE = "test-engine";
  private static final String TEST_ENGINE_1 = "1";
  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private IndexHandlerProvider indexHandlerProvider;

  @Autowired
  private ImportSchedulerFactory importSchedulerFactory;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ImportProgressReporter importProgressReporter;

  private List<PaginatedImportService> paginatedImportServices;


  @Before
  public void setUp() throws OptimizeException {
    for (ImportScheduler scheduler : importSchedulerFactory.getInstances().values()) {
      scheduler.clearQueue();
    }

    paginatedImportServices = mockPaginatedImportServices();

    Map<String, ImportService> allServicesMap = getAllImportServiceMap(paginatedImportServices);
    when(importServiceProvider.getAllEngineServices(Mockito.any())).thenReturn(allServicesMap);

    mockIndexHandlers(paginatedImportServices, indexHandlerProvider);

    when(importServiceProvider.getPagedServices(Mockito.any())).thenReturn(paginatedImportServices);
    when(importServiceProvider.getPaginatedImportService(Mockito.any(), Mockito.any())).thenReturn(paginatedImportServices.get(0));
    when(importServiceProvider.getVariableImportService(Mockito.any())).thenReturn((VariableImportService) allServicesMap.get("variable"));
    when(importServiceProvider.getProcessInstanceImportService(Mockito.any())).thenReturn((ProcessInstanceImportService) allServicesMap.get("pi-is"));
  }


  @After
  public void tearDown() {
    Mockito.reset(importServiceProvider);
    Mockito.reset(indexHandlerProvider);
  }

  @Test
  public void testJobsScheduled () {
    ImportScheduler importScheduler = importSchedulerFactory.getInstances().values().iterator().next();
    importScheduler.scheduleNewImportRound();
    assertThat(importScheduler.importScheduleJobs.size(),
        is(importServiceProvider.getPagedServices(TEST_ENGINE).size())
    );
  }

  @Test
  public void testOnlyJobsOfOneEngineAreInQueue() throws InterruptedException, OptimizeException {
    List<PaginatedImportService> services = mockPaginatedImportServices();
    when(importServiceProvider.getPagedServices(TEST_ENGINE_1)).thenReturn(services);

    ImportScheduler importScheduler = importSchedulerFactory.getInstances().values().iterator().next();
    importScheduler.scheduleNewImportRound();
    assertThat(importScheduler.getEngineName(), is(TEST_ENGINE_1));

    for (ImportScheduleJob job : importScheduler.importScheduleJobs) {
      assertThat(job.getEngineAlias(), is(TEST_ENGINE_1));
    }
  }

  @Test
  public void allImportsAreTriggered() throws InterruptedException, OptimizeException {

    // given
    List<PaginatedImportService> services = mockPaginatedImportServices();
    when(importServiceProvider.getPagedServices(Mockito.any())).thenReturn(services);

    ImportScheduler importScheduler = importSchedulerFactory.getInstances().values().iterator().next();
    importScheduler.scheduleNewImportRound();

    // when
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();

    // then
    for (ImportService service : services) {
      verify(service, times(1)).executeImport(Mockito.any());
    }
  }

  @Test
  public void testProcessInstanceImportScheduledBasedOnActivities () throws Exception {
    PaginatedImportService paginatedImportService = paginatedImportServices.get(0);

    ImportResult result = getConstructResult(paginatedImportService);

    when(paginatedImportService.executeImport(Mockito.any())).thenReturn(result);
    ImportScheduler importScheduler = importSchedulerFactory.getInstances().values().iterator().next();

    importScheduler.scheduleNewImportRound();

    //when
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();

    assertThat(importScheduler.importScheduleJobs.size(), is(5));
    ImportScheduleJob piJob = importScheduler.importScheduleJobs.poll();
    assertThat(piJob, is(instanceOf(IdBasedImportScheduleJob.class)));
    assertThat(((IdBasedImportScheduleJob)piJob).getIdsToFetch(), is(notNullValue()));
    assertThat(((IdBasedImportScheduleJob)piJob).getIdsToFetch().toArray()[0], is(TEST_ID));
  }

  private ImportResult getConstructResult(PaginatedImportService paginatedImportService) {
    ImportResult result = new ImportResult();
    result.setEngineHasStillNewData(true);
    result.setSearchedSize(1);
    Set<String> piIds = new HashSet<>();
    piIds.add(TEST_ID);
    result.setIdsToFetch(piIds);
    result.setElasticSearchType(paginatedImportService.getElasticsearchType());
    result.setIndexHandlerType(paginatedImportService.getIndexHandlerType());
    return result;
  }

  @Test
  public void testResetAfterPeriod () throws Exception {
    // given
    List<PaginatedImportService> services = mockPaginatedImportServices();
    when(importServiceProvider.getPagedServices(Mockito.any())).thenReturn(services);

    ChronoUnit unit = ChronoUnit.valueOf(configurationService.getImportResetIntervalUnit().toUpperCase());
    LocalDateTime expectedReset = LocalDateTime.now().plus(configurationService.getImportResetIntervalValue(), unit);
    long toSleep = LocalDateTime.now().until(expectedReset, unit);
    ImportScheduler importScheduler = importSchedulerFactory.getInstances().values().iterator().next();
    
    //when
    Thread.currentThread().sleep(Math.max(toSleep,1000L));
    importScheduler.checkAndResetImportIndexing();

    // then
    Mockito.verify(
        indexHandlerProvider.getIndexHandler(
            services.get(0).getElasticsearchType(),
            services.get(0).getIndexHandlerType(),
            "1"),
        times(1)
    ).resetImportIndex();
    assertThat(importScheduler.getLastReset().isAfter(LocalDateTime.now().minusSeconds(2)), is(true));

    //clean up mocks
    Mockito.reset(services.toArray());
  }

  @Test
  public void testResetIfEntitiesWereMissedDuringImport () throws Exception {
    // given
    List<PaginatedImportService> services = mockPaginatedImportServices();
    when(importServiceProvider.getPagedServices(Mockito.any())).thenReturn(services);
    when(importProgressReporter.allEntitiesAreImported()).thenReturn(true);
    ImportScheduler importScheduler = importSchedulerFactory.getInstances().values().iterator().next();

    //when
    importScheduler.checkAndResetImportIndexing();

    // then
    Mockito.verify(
        indexHandlerProvider.getIndexHandler(
            services.get(0).getElasticsearchType(),
            services.get(0).getIndexHandlerType(),
            "1"),
        times(1)
    ).resetImportIndex();

    assertThat(importScheduler.getLastReset().isAfter(LocalDateTime.now().minusSeconds(2)), is(true));

    //clean up mocks
    Mockito.reset(services.toArray());
  }

  protected List<PaginatedImportService> mockPaginatedImportServices() throws OptimizeException {
    List<PaginatedImportService> services = super.mockPaginatedImportServices();

    for (PaginatedImportService s : services) {
      when(importServiceProvider.getImportService(Mockito.eq(s.getElasticsearchType()), Mockito.any())).thenReturn(s);
    }
    return services;
  }


}
