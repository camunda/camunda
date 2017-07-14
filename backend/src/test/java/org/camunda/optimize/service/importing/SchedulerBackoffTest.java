package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.job.schedule.ImportScheduleJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

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
  private ImportScheduler importScheduler;

  @Autowired
  private BackoffService backoffService;

  private List<PaginatedImportService> services;

  @Before
  public void setUp() throws OptimizeException {
    backoffService.resetBackoffCounters();
    importScheduler.importScheduleJobs.clear();

    services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);
  }

  @Test
  public void testNotBackingOffIfImportPagesFound() throws Exception {
    //given
    ImportResult result = new ImportResult();
    result.setEngineHasStillNewData(true);
    when(services.get(0).executeImport()).thenReturn(result);
    importScheduler.scheduleNewImportRound();

    //when
    importScheduler.executeNextJob();

    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));
  }

  @Test
  public void testBackingOffIfNoImportPagesFound() throws Exception {
    //given
    List<PaginatedImportService> services = mockImportServices();
    when(importServiceProvider.getPagedServices()).thenReturn(services);

    ImportResult zeroResult = new ImportResult();
    zeroResult.setEngineHasStillNewData(false);
    when(services.get(0).executeImport()).thenReturn(zeroResult);

    ImportResult singleResult = new ImportResult();
    singleResult.setEngineHasStillNewData(true);
    when(services.get(1).executeImport()).thenReturn(singleResult);

    importScheduler.scheduleNewImportRound();

    //when
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();

    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(1L));
    assertThat(backoffService.getBackoffCounter(services.get(1).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
  }

  @Test
  public void testGeneralBackoffIncreaseWithoutJobs() throws Exception {
    // given
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));
    importScheduler.setSkipBackoffToCheckForNewDataInEngine(false);

    //when
    importScheduler.executeNextJob();

    //then
    assertThat(backoffService.getGeneralBackoffCounter(), is(1L));
  }

  @Test
  public void everySecondsImportRoundBackoffIsSkipped() throws Exception {
    // given
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));
    importScheduler.setSkipBackoffToCheckForNewDataInEngine(true);

    //when
    importScheduler.executeNextJob();

    // then the backoff is skipped the first time
    assertThat(backoffService.getGeneralBackoffCounter(), is(0L));

    // when I finish all jobs and there is another try for backoff
    while (importScheduler.hasStillJobsToExecute()) {
      importScheduler.getNextToExecute();
    }
    importScheduler.executeNextJob();

    //then the backoff is actually performed the seconds time
    assertThat(backoffService.getGeneralBackoffCounter(), is(1L));
  }

  @Test
  public void testGeneralBackoffDoesNotExceedMax() throws Exception {
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));

    //when
    importScheduler.executeNextJob();
    //since in reality executeNextJob will be invoked by sleeping thread, let it sleep properly
    Thread.sleep(1000);
    importScheduler.executeNextJob();
    Thread.sleep(1000);
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();

    //then
    assertThat(backoffService.getGeneralBackoffCounter(), is(3L));
  }


  @Test
  public void testBackoffResetAfterPage() throws OptimizeException {
    //given
    //right after instantiation backoff is 0
    importScheduler.scheduleNewImportRound();
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getBackoffCounter(services.get(1).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getBackoffCounter(services.get(2).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));


    importScheduler.executeNextJob();
    //initial execution increases backoff and schedules jobs
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(1L));
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    //there were still no pages returned -> backoff is 2
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(2L));

    //return one page from first import service

    ImportResult result = new ImportResult();
    result.setEngineHasStillNewData(true);
    for (PaginatedImportService m : services) {
      when(m.executeImport()).thenReturn(result);
    }

    //when
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    importScheduler.executeNextJob();
    assertThat(backoffService.getGeneralBackoffCounter(), is(BackoffService.STARTING_BACKOFF));

    importScheduler.executeNextJob();
    importScheduler.executeNextJob();

    //then
    assertThat(backoffService.getBackoffCounter(services.get(0).getElasticsearchType()), is(BackoffService.STARTING_BACKOFF));
  }

  @Test
  public void testBackoffNotExceedingMax() throws Exception {
    ImportScheduleJob toExecute = new PageBasedImportScheduleJob();
    toExecute.setImportService(services.get(0));

    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(1L));
    assertThat(backoffService.calculateJobBackoff(true, toExecute), is(BackoffService.STARTING_BACKOFF));
    //does not increase after 2
    importScheduler.executeNextJob();
    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(2L));
    importScheduler.executeNextJob();
    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(3L));
    importScheduler.executeNextJob();
    assertThat(backoffService.calculateJobBackoff(false, toExecute), is(3L));
  }
}
