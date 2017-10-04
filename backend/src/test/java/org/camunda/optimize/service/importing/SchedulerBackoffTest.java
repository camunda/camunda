package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessInstanceImportService;
import org.camunda.optimize.service.importing.impl.VariableImportService;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class SchedulerBackoffTest extends AbstractSchedulerTest {

  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private IndexHandlerProvider indexHandlerProvider;

  @Autowired
  private ImportSchedulerFactory importSchedulerFactory;

  @Autowired
  private BackoffService backoffService;

  private List<PaginatedImportService> services;

  @Before
  public void setUp() throws OptimizeException {
    backoffService.resetBackoffCounters();
    getImportScheduler().importScheduleJobs.clear();

    services = mockPaginatedImportServices();
    Map<String, ImportService> allServicesMap = getAllImportServiceMap(services);
    when(importServiceProvider.getAllEngineServices(Mockito.any())).thenReturn(allServicesMap);

    mockIndexHandlers(services, indexHandlerProvider);
    when(importServiceProvider.getPagedServices(Mockito.any())).thenReturn(services);
    when(importServiceProvider.getVariableImportService(Mockito.any()))
        .thenReturn((VariableImportService) allServicesMap.get("variable"));
    when(importServiceProvider.getProcessInstanceImportService(Mockito.any()))
        .thenReturn((ProcessInstanceImportService) allServicesMap.get("pi-is"));
  }

  private ImportScheduler getImportScheduler() {
    return importSchedulerFactory.getInstances().values().iterator().next();
  }

  @After
  public void tearDown() {
    Mockito.reset(importServiceProvider);
    Mockito.reset(indexHandlerProvider);
  }

  @Test
  public void testNotBackingOffIfImportPagesFound() throws Exception {
    //given
    ImportResult result = new ImportResult();
    result.setEngineHasStillNewData(true);
    when(services.get(0).executeImport(Mockito.any())).thenReturn(result);
    getImportScheduler().scheduleNewImportRound();

    //when
    getImportScheduler().executeNextJob();

    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));
  }

  @Test
  public void testBackingOffIfNoImportPagesFound() throws Exception {
    //given
    List<PaginatedImportService> services = mockPaginatedImportServices();
    when(importServiceProvider.getPagedServices(Mockito.any())).thenReturn(services);

    PaginatedImportService zeroPaginatedImportService = services.get(0);
    ImportResult zeroResult = new ImportResult();
    zeroResult.setEngineHasStillNewData(false);
    zeroResult.setElasticSearchType(zeroPaginatedImportService.getElasticsearchType());
    zeroResult.setIndexHandlerType(zeroPaginatedImportService.getIndexHandlerType());

    when(zeroPaginatedImportService.executeImport(Mockito.any())).thenReturn(zeroResult);

    PaginatedImportService singlePaginatedImportService = services.get(1);
    ImportResult singleResult = new ImportResult();
    singleResult.setEngineHasStillNewData(true);
    singleResult.setElasticSearchType(singlePaginatedImportService.getElasticsearchType());
    singleResult.setIndexHandlerType(singlePaginatedImportService.getIndexHandlerType());

    when(singlePaginatedImportService.executeImport(Mockito.any())).thenReturn(singleResult);

    getImportScheduler().scheduleNewImportRound();

    //when
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();

    assertThat(backoffService.getBackoffCounter(zeroPaginatedImportService.getElasticsearchType()), is(1L));
    assertThat(backoffService.getBackoffCounter(singlePaginatedImportService.getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
  }

  @Test
  public void testGeneralBackoffIncreaseWithoutJobs() throws Exception {
    // given
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));
    getImportScheduler().setSkipBackoffToCheckForNewDataInEngine(false);

    //when
    getImportScheduler().executeNextJob();

    //then
    assertThat(backoffService.getGeneralBackoffCounter(), is(1L));
  }

  @Test
  public void everySecondsImportRoundBackoffIsSkipped() throws Exception {
    // given
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));
    getImportScheduler().setSkipBackoffToCheckForNewDataInEngine(true);

    //when
    getImportScheduler().executeNextJob();

    // then the backoff is skipped the first time
    assertThat(backoffService.getGeneralBackoffCounter(), is(0L));

    // when I finish all jobs and there is another try for backoff
    while (getImportScheduler().hasStillJobsToExecute()) {
      getImportScheduler().getNextToExecute();
    }
    getImportScheduler().executeNextJob();

    //then the backoff is actually performed the seconds time
    assertThat(backoffService.getGeneralBackoffCounter(), is(1L));
  }

  @Test
  public void doNotBackoffWhenExecutableJobExists() throws Exception {
    // given
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));

    // when
    PageBasedImportScheduleJob pageBasedImportScheduleJob = new PageBasedImportScheduleJob(0,0, 0, "test");
    pageBasedImportScheduleJob.setDateUntilExecutionIsBlocked(LocalDateTime.now().minus(1L, ChronoUnit.MINUTES));
    getImportScheduler().importScheduleJobs.add(pageBasedImportScheduleJob);
    getImportScheduler().scheduleNewImportRound();

    // then
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));

  }

  @Test
  public void testGeneralBackoffDoesNotExceedMax() throws Exception {
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));

    //when
    getImportScheduler().executeNextJob();
    //since in reality executeNextJob will be invoked by sleeping thread, let it sleep properly
    Thread.sleep(1000);
    getImportScheduler().executeNextJob();
    Thread.sleep(1000);
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();

    //then
    assertThat(backoffService.getGeneralBackoffCounter(), is(3L));
  }


  @Test
  public void testBackoffResetAfterPage() throws OptimizeException {
    //given
    //right after instantiation backoff is 0
    getImportScheduler().scheduleNewImportRound();
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getBackoffCounter(services.get(1).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getBackoffCounter(services.get(2).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));


    getImportScheduler().executeNextJob();
    //initial execution increases backoff and schedules jobs
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(1L));
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();
    //there were still no pages returned -> backoff is 2
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(2L));

    //return one page from first import service


    for (PaginatedImportService importService : services) {
      ImportResult result = new ImportResult();
      result.setEngineHasStillNewData(true);
      result.setElasticSearchType(importService.getElasticsearchType());
      result.setIndexHandlerType(importService.getIndexHandlerType());

      when(importService.executeImport(Mockito.any())).thenReturn(result);
    }

    //when
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));

    getImportScheduler().executeNextJob();
    getImportScheduler().executeNextJob();

    //then
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
  }

  @Test
  public void testBackoffNotExceedingMax() throws Exception {
    ImportScheduleJob toExecute = new PageBasedImportScheduleJob(0,0, 0, "test");
    toExecute.setElasticsearchType(services.get(0).getElasticsearchType());

    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(1L));
    assertThat(backoffService.calculateJobBackoff(true, toExecute), is(BackoffService.STARTING_BACKOFF));
    //does not increase after 2
    getImportScheduler().executeNextJob();
    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(2L));
    getImportScheduler().executeNextJob();
    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(3L));
    getImportScheduler().executeNextJob();
    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(3L));
  }

  protected List<PaginatedImportService> mockPaginatedImportServices() throws OptimizeException {
    List<PaginatedImportService> services = super.mockPaginatedImportServices();

    for (PaginatedImportService s : services) {
      when(importServiceProvider.getImportService(Mockito.eq(s.getElasticsearchType()), Mockito.any())).thenReturn(s);
    }
    return services;
  }
}
