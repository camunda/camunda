package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
import org.camunda.optimize.service.importing.job.impl.EventImportJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class PaginatedImportServiceTest {

  private static final String TEST_ACTIVITY_ID = "testActivityId";

  @InjectMocks
  private ActivityImportService activityImportService;

  @Mock
  private ImportJobExecutor importJobExecutor;

  @Mock
  private ImportStrategyProvider importStrategyProvider;

  @Mock
  private TotalQuantityBasedImportStrategy importStrategy;

  //do not remove as it is used for autowiring
  @Spy
  @Autowired
  private MissingActivityFinder missingActivityFinder;

  @Autowired
  @Spy
  private ConfigurationService configurationService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(importStrategyProvider.getImportStrategyInstance()).thenReturn(importStrategy);
  }

  @Test
  public void importIsDoneInPages() throws Exception {
    // given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    when(importStrategy.fetchHistoricActivityInstances())
      .thenReturn(resultList.subList(0, 2))
      .thenReturn(resultList.subList(2, 4))
      .thenReturn(resultList.subList(4, 5))
      .thenReturn(Collections.emptyList());

    // when
    activityImportService.executeImport();
    activityImportService.executeImport();
    activityImportService.executeImport();
    activityImportService.executeImport();

    // then
    verify(importStrategy, times(4))
      .fetchHistoricActivityInstances();
    List<EventImportJob> eventImportJobs = getEventImportJobs();
    assertThat(eventImportJobs.size(), is(3));
    assertThat(eventImportJobs.get(0).getEntitiesToImport().size(), is(2));
    assertThat(eventImportJobs.get(1).getEntitiesToImport().size(), is(2));
    assertThat(eventImportJobs.get(2).getEntitiesToImport().size(), is(1));
  }

  @Test
  public void importCanBeExecutedSeveralTimes() throws Exception {
    // given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    when(importStrategy.fetchHistoricActivityInstances())
      .thenReturn(resultList.subList(0, 2))
      .thenReturn(Collections.emptyList());
    activityImportService.executeImport();
    activityImportService.executeImport();
    when(importStrategy.fetchHistoricActivityInstances())
      .thenReturn(resultList.subList(2, 4))
      .thenReturn(resultList.subList(4, 5))
      .thenReturn(Collections.emptyList());

    // when
    activityImportService.executeImport();
    activityImportService.executeImport();
    activityImportService.executeImport();

    // then
    verify(importStrategy, times(5))
      .fetchHistoricActivityInstances();
    List<EventImportJob> eventImportJobs = getEventImportJobs();
    assertThat(eventImportJobs.size(), is(3));
    assertThat(eventImportJobs.get(0).getEntitiesToImport().size(), is(2));
    assertThat(eventImportJobs.get(1).getEntitiesToImport().size(), is(2));
    assertThat(eventImportJobs.get(2).getEntitiesToImport().size(), is(1));
  }

  @Test
  public void importCanBeReset() throws Exception {
    // given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    when(importStrategy.fetchHistoricActivityInstances())
      .thenReturn(resultList.subList(4, 5))
      .thenReturn(Collections.emptyList());
    activityImportService.executeImport();
    activityImportService.executeImport();
    when(importStrategy.fetchHistoricActivityInstances())
      .thenReturn(resultList.subList(0, 2))
      .thenReturn(resultList.subList(2, 4))
      .thenReturn(Collections.emptyList());

    // when
    activityImportService.resetImportStartIndex();
    activityImportService.executeImport();
    activityImportService.executeImport();
    activityImportService.executeImport();

    // then
    verify(importStrategy, times(5))
      .fetchHistoricActivityInstances();
    List<EventImportJob> eventImportJobs = getEventImportJobs();
    assertThat(eventImportJobs.size(), is(3));
    assertThat(eventImportJobs.get(0).getEntitiesToImport().size(), is(1));
    assertThat(eventImportJobs.get(1).getEntitiesToImport().size(), is(2));
    assertThat(eventImportJobs.get(2).getEntitiesToImport().size(), is(2));
  }

  private List<EventImportJob> getEventImportJobs() throws InterruptedException {
    ArgumentCaptor<EventImportJob> captor = ArgumentCaptor.forClass(EventImportJob.class);
    verify(importJobExecutor, atLeast(3)).executeImportJob(captor.capture());
    List<EventImportJob> eventImportJobs = new LinkedList<>();
    for (int i=0; i<captor.getAllValues().size(); i++) {
      Object value = captor.getAllValues().get(i);
      if (value instanceof EventImportJob) {
        eventImportJobs.add((EventImportJob) value);
      }
    }
    return eventImportJobs;
  }

  private List<HistoricActivityInstanceEngineDto> setupInputData() {
    List<HistoricActivityInstanceEngineDto> resultList = new ArrayList<>(5);
    for (int i = 0; i < 5; i++) {
      HistoricActivityInstanceEngineDto instance = new HistoricActivityInstanceEngineDto();
      instance.setId("testId" + i);
      instance.setActivityId(TEST_ACTIVITY_ID);
      instance.setProcessInstanceId("procInstId");
      resultList.add(instance);
    }
    return resultList;
  }
}
