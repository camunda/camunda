package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class PaginatedImportServiceTest {

  private static final String TEST_ACTIVITY_ID = "testActivityId";

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @InjectMocks
  private ActivityImportService activityImportService;

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  @Mock
  private ImportJobExecutor importJobExecutor;

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
  }

  @Test
  public void unreasonableMaxPageSizeThrowsError() {
    // given
    when(configurationService.getEngineImportMaxPageSize()).thenReturn(0);

    // then
    exception.expect(OptimizeRuntimeException.class);

    // when
    activityImportService.executeImport();
  }

  @Test
  public void importIsDoneInPages() throws Exception {
    // given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
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
    verify(engineEntityFetcher, times(4))
      .fetchHistoricActivityInstances(anyInt(), anyInt());
    ArgumentCaptor<ImportJob> captor = ArgumentCaptor.forClass(ImportJob.class);
    verify(importJobExecutor, times(3)).executeImportJob(captor.capture());
    assertThat(captor.getAllValues().get(0).getEntitiesToImport().size(), is(2));
    assertThat(captor.getAllValues().get(1).getEntitiesToImport().size(), is(2));
    assertThat(captor.getAllValues().get(2).getEntitiesToImport().size(), is(1));

  }

  @Test
  public void importCanBeExecutedSeveralTimes() throws Exception {
    // given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
      .thenReturn(resultList.subList(0, 2))
      .thenReturn(Collections.emptyList());
    activityImportService.executeImport();
    activityImportService.executeImport();
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
      .thenReturn(resultList.subList(2, 4))
      .thenReturn(resultList.subList(4, 5))
      .thenReturn(Collections.emptyList());

    // when
    activityImportService.executeImport();
    activityImportService.executeImport();
    activityImportService.executeImport();

    // then
    verify(engineEntityFetcher, times(5))
      .fetchHistoricActivityInstances(anyInt(), anyInt());
    ArgumentCaptor<ImportJob> captor = ArgumentCaptor.forClass(ImportJob.class);
    verify(importJobExecutor, times(3)).executeImportJob(captor.capture());
    assertThat(captor.getAllValues().get(0).getEntitiesToImport().size(), is(2));
    assertThat(captor.getAllValues().get(1).getEntitiesToImport().size(), is(2));
    assertThat(captor.getAllValues().get(2).getEntitiesToImport().size(), is(1));
  }

  @Test
  public void importCanBeReset() throws Exception {
    // given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
      .thenReturn(resultList.subList(4, 5))
      .thenReturn(Collections.emptyList());
    activityImportService.executeImport();
    activityImportService.executeImport();
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
      .thenReturn(resultList.subList(0, 2))
      .thenReturn(resultList.subList(2, 4))
      .thenReturn(Collections.emptyList());

    // when
    activityImportService.resetImportStartIndex();
    activityImportService.executeImport();
    activityImportService.executeImport();
    activityImportService.executeImport();

    // then
    verify(engineEntityFetcher, times(5))
      .fetchHistoricActivityInstances(anyInt(), anyInt());
    ArgumentCaptor<ImportJob> captor = ArgumentCaptor.forClass(ImportJob.class);
    verify(importJobExecutor, times(3)).executeImportJob(captor.capture());
    assertThat(captor.getAllValues().get(0).getEntitiesToImport().size(), is(1));
    assertThat(captor.getAllValues().get(1).getEntitiesToImport().size(), is(2));
    assertThat(captor.getAllValues().get(2).getEntitiesToImport().size(), is(2));
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
