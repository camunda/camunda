package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.job.schedule.IdBasedImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.util.ConfigurationService;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ImportSchedulerTest {

  public static final String TEST_ID = "testId";
  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private ImportScheduler importScheduler;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ImportProgressReporter importProgressReporter;

  private List<PaginatedImportService> services;

  @Before
  public void setUp() throws OptimizeException {
    importScheduler.resetBackoffCounters();
    importScheduler.importScheduleJobs.clear();

    services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);
  }

  @After
  public void tearDown() {
    Mockito.reset(importServiceProvider);
  }

  @Test
  public void testJobsScheduled () {
    importScheduler.scheduleProcessEngineImport();
    assertThat(importScheduler.importScheduleJobs.size(),is(importServiceProvider.getPagedServices().size()));
  }

  @Test
  public void allImportsAreTriggered() throws InterruptedException, OptimizeException {

    // given
    List<PaginatedImportService> services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);
    importScheduler.scheduleProcessEngineImport();

    // when
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();

    // then
    for (ImportService service : services) {
      verify(service, times(1)).executeImport();
    }
  }

  private List<PaginatedImportService> mockImportServices() throws OptimizeException {
    ArrayList<PaginatedImportService> services = new ArrayList<>();
    ActivityImportService activityImportService = mock(ActivityImportService.class);
    when(activityImportService.getElasticsearchType()).thenReturn("activity");
    services.add(activityImportService);
    ProcessDefinitionImportService processDefinitionImportService = mock(ProcessDefinitionImportService.class);
    when(processDefinitionImportService.getElasticsearchType()).thenReturn("pd-is");
    services.add(processDefinitionImportService);
    ProcessDefinitionXmlImportService processDefinitionXmlImportService = mock(ProcessDefinitionXmlImportService.class);
    when(processDefinitionXmlImportService.getElasticsearchType()).thenReturn("pd-xml");
    services.add(processDefinitionXmlImportService);
    for (PaginatedImportService service : services) {
      when(service.executeImport()).thenReturn(new ImportResult());
    }
    return services;
  }

  @Test
  public void testProcessInstanceImportScheduledBasedOnaActivities () throws Exception {
    ImportResult result = new ImportResult();
    result.setPagesPassed(1);
    Set<String> piIds = new HashSet<>();
    piIds.add(TEST_ID);
    result.setIdsToFetch(piIds);
    when(services.get(0).executeImport()).thenReturn(result);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();

    assertThat(importScheduler.importScheduleJobs.size(), is(5));
    ImportScheduleJob piJob = importScheduler.importScheduleJobs.poll();
    assertThat(piJob, is(instanceOf(IdBasedImportScheduleJob.class)));
    assertThat(((IdBasedImportScheduleJob)piJob).getIdsToFetch(), is(notNullValue()));
    assertThat(((IdBasedImportScheduleJob)piJob).getIdsToFetch().toArray()[0], is(TEST_ID));
  }

  @Test
  public void testNotBackingOffIfImportPagesFound() throws Exception {
    //given
    ImportResult result = new ImportResult();
    result.setPagesPassed(1);
    when(services.get(0).executeImport()).thenReturn(result);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();

    assertThat(importScheduler.getBackoffCounter(services.get(0).getElasticsearchType()), is(ImportScheduler.STARTING_BACKOFF));
    assertThat(importScheduler.getGeneralBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));
  }

  @Test
  public void testBackingOffIfNoImportPagesFound() throws Exception {
    //given
    List<PaginatedImportService> services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);
    ImportResult result = new ImportResult();
    result.setPagesPassed(0);
    when(services.get(0).executeImport()).thenReturn(result);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();

    assertThat(importScheduler.getBackoffCounter(services.get(0).getElasticsearchType()), is(1L));
  }

  @Test
  public void testGeneralBackoffIncreaseWithoutJobs() throws Exception {
    assertThat(importScheduler.getGeneralBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));

    //when
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getGeneralBackoffCounter(), is(1L));
  }

  @Test
  public void testGeneralBackoffDoesNotExceedMax() throws Exception {
    assertThat(importScheduler.getGeneralBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));

    //when
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getGeneralBackoffCounter(), is(3L));
  }



  @Test
  public void testResetAfterPeriod () throws Exception {
    // given
    List<PaginatedImportService> services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);

    LocalDateTime expectedReset = LocalDateTime.now().plus(Double.valueOf(configurationService.getImportResetInterval()).longValue(), ChronoUnit.HOURS);
    long toSleep = LocalDateTime.now().until(expectedReset, ChronoUnit.MILLIS);
    
    //when
    Thread.currentThread().sleep(Math.max(toSleep,1000L));
    importScheduler.checkAndResetImportIndexing();

    // then
    Mockito.verify(importServiceProvider.getPagedServices().get(0),times(1)).resetImportStartIndex();
    assertThat(importScheduler.getLastReset().isAfter(LocalDateTime.now().minusSeconds(2)), is(true));

    //clean up mocks
    Mockito.reset(services.toArray());
  }

  @Test
  public void testResetIfEntitiesWereMissedDuringImport () throws Exception {
    // given
    List<PaginatedImportService> services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);
    when(importProgressReporter.allEntitiesAreImported()).thenReturn(true);

    //when
    importScheduler.checkAndResetImportIndexing();

    // then
    Mockito.verify(importServiceProvider.getPagedServices().get(0),times(1)).resetImportStartIndex();
    assertThat(importScheduler.getLastReset().isAfter(LocalDateTime.now().minusSeconds(2)), is(true));

    //clean up mocks
    Mockito.reset(services.toArray());
  }

  @Test
  public void testBackoffResetAfterPage() throws OptimizeException {
    //given
    //right after instantiation backoff is 0
    importScheduler.scheduleProcessEngineImport();
    assertThat(importScheduler.getBackoffCounter(services.get(0).getElasticsearchType()), is(ImportScheduler.STARTING_BACKOFF));
    assertThat(importScheduler.getBackoffCounter(services.get(1).getElasticsearchType()), is(ImportScheduler.STARTING_BACKOFF));
    assertThat(importScheduler.getBackoffCounter(services.get(2).getElasticsearchType()), is(ImportScheduler.STARTING_BACKOFF));
    assertThat(importScheduler.getGeneralBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));


    importScheduler.executeJob();
    //initial execution increases backoff and schedules jobs
    assertThat(importScheduler.getBackoffCounter(services.get(0).getElasticsearchType()), is(1L));
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();
    //there were still no pages returned -> backoff is 2
    assertThat(importScheduler.getBackoffCounter(services.get(0).getElasticsearchType()), is(2L));

    //return one page from first import service

    ImportResult result = new ImportResult();
    result.setPagesPassed(1);
    for (PaginatedImportService m : services) {
      when(m.executeImport()).thenReturn(result);
    }

    //when
    importScheduler.executeJob();
    assertThat(importScheduler.getGeneralBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));

    importScheduler.executeJob();
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getBackoffCounter(services.get(0).getElasticsearchType()), is(ImportScheduler.STARTING_BACKOFF));
  }

  @Test
  public void testBackoffNotExceedingMax() throws Exception {
    ImportScheduleJob toExecute = new PageBasedImportScheduleJob();
    toExecute.setImportService(services.get(0));

    assertThat(importScheduler.calculateJobBackoff(0, toExecute), is(1L));
    assertThat(importScheduler.calculateJobBackoff(1, toExecute), is(ImportScheduler.STARTING_BACKOFF));
    //does not increase after 2
    importScheduler.executeJob();
    assertThat(importScheduler.calculateJobBackoff(0, toExecute), is(2L));
    importScheduler.executeJob();
    assertThat(importScheduler.calculateJobBackoff(0, toExecute), is(3L));
    importScheduler.executeJob();
    assertThat(importScheduler.calculateJobBackoff(0, toExecute), is(3L));
  }

}
