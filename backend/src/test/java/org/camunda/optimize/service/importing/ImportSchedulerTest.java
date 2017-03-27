package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
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
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ImportSchedulerTest {

  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private ImportScheduler importScheduler;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ImportProgressReporter importProgressReporter;

  private List<ImportService> services;

  @Before
  public void setUp() {
    importScheduler.resetBackoffCounter();
    importScheduler.importScheduleJobs.clear();

    services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
  }

  @After
  public void tearDown() {
    Mockito.reset(importServiceProvider);
  }

  @Test
  public void testJobsScheduled () {
    importScheduler.scheduleProcessEngineImport();
    assertThat(importScheduler.importScheduleJobs.size(),is(importServiceProvider.getServices().size()));
  }

  @Test
  public void allImportsAreTriggered() throws InterruptedException {

    // given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
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

  private List<ImportService> mockImportServices() {
    List<ImportService> services = new ArrayList<>();
    services.add(mock(ActivityImportService.class));
    services.add(mock(ProcessDefinitionImportService.class));
    services.add(mock(ProcessDefinitionXmlImportService.class));
    return services;
  }

  @Test
  public void testNotBackingOffIfImportPagesFound() throws Exception {
    //given
    when(services.get(0).executeImport()).thenReturn(1);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();

    assertThat(importScheduler.getBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));
  }

  @Test
  public void testBackingOffIfNoImportPagesFound() throws Exception {
    //given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
    when(services.get(0).executeImport()).thenReturn(0);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();

    assertThat(importScheduler.getBackoffCounter(), is(1L));
  }

  @Test
  public void testBackoffIncreaseWithoutJobs() throws Exception {
    assertThat(importScheduler.getBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));

    //when
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getBackoffCounter(), is(1L));
  }

  @Test
  public void testResetAfterPeriod () throws Exception {
    // given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);

    LocalDateTime expectedReset = LocalDateTime.now().plus(Double.valueOf(configurationService.getImportResetInterval()).longValue(), ChronoUnit.HOURS);
    long toSleep = LocalDateTime.now().until(expectedReset, ChronoUnit.MILLIS);
    
    //when
    Thread.currentThread().sleep(Math.max(toSleep,1000L));
    importScheduler.checkAndResetImportIndexing();

    // then
    Mockito.verify(importServiceProvider.getServices().get(0),times(1)).resetImportStartIndex();
    assertThat(importScheduler.getLastReset().isAfter(LocalDateTime.now().minusSeconds(2)), is(true));

    //clean up mocks
    Mockito.reset(services.toArray());
  }

  @Test
  public void testResetIfEntitiesWereMissedDuringImport () throws Exception {
    // given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
    when(importProgressReporter.allEntitiesAreImported()).thenReturn(true);

    //when
    importScheduler.checkAndResetImportIndexing();

    // then
    Mockito.verify(importServiceProvider.getServices().get(0),times(1)).resetImportStartIndex();
    assertThat(importScheduler.getLastReset().isAfter(LocalDateTime.now().minusSeconds(2)), is(true));

    //clean up mocks
    Mockito.reset(services.toArray());
  }

  @Test
  public void testBackoffResetAfterPage() {
    //given
    //right after instantiation backoff is 0
    assertThat(importScheduler.getBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));

    importScheduler.executeJob();
    //initial execution increases backoff and schedules jobs
    assertThat(importScheduler.getBackoffCounter(), is(1L));

    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();
    //there were still no pages returned -> backoff is 2
    assertThat(importScheduler.getBackoffCounter(), is(2L));

    //return one page from first import service

    when(services.get(0).executeImport()).thenReturn(1);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getBackoffCounter(), is(ImportScheduler.STARTING_BACKOFF));
  }

  @Test
  public void testBackoffNotExceedingMax() throws Exception {
    assertThat(importScheduler.calculateBackoff(0), is(1L));
    assertThat(importScheduler.calculateBackoff(1), is(ImportScheduler.STARTING_BACKOFF));
    //does not increase after 2
    importScheduler.executeJob();
    importScheduler.executeJob();
    importScheduler.executeJob();
    assertThat(importScheduler.calculateBackoff(0), is(2L));
    importScheduler.executeJob();
    assertThat(importScheduler.calculateBackoff(0), is(3L));
    importScheduler.executeJob();
    assertThat(importScheduler.calculateBackoff(0), is(3L));
  }

}
