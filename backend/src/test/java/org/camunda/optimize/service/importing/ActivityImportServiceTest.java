package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
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
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ActivityImportServiceTest {

  private static final String TEST_ACTIVITY_ID = "testActivityId";
  private static final String TEST_ID = "testId";

  @InjectMocks
  private ActivityImportService underTest;

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  @Mock
  private EventsWriter eventsWriter;

  @Mock
  private ImportJobExecutor importJobExecutor;

  @Spy
  @Autowired
  private ConfigurationService configurationService;

  @Spy
  @Autowired
  private MissingActivityFinder missingActivityFinder;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void executeImport() throws Exception {
    //given
    List<HistoricActivityInstanceEngineDto> resultList = setupInputData();
    setupEngineEntityFetcher(resultList);

    //when
    underTest.executeImport();

    //then
    ensureThatAJobWithCorrectIdWasCreated();
  }

  private void ensureThatAJobWithCorrectIdWasCreated() throws InterruptedException {
    ArgumentCaptor<EventImportJob> eventCaptor = ArgumentCaptor.forClass(EventImportJob.class);
    verify(importJobExecutor).executeImportJob(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues().size(), is(1));
    assertThat(eventCaptor.getAllValues().get(0).getEntitiesToImport().size(), is(1));
    EventDto eventDto = eventCaptor.getAllValues().get(0).getEntitiesToImport().get(0);
    assertThat(eventDto.getId(), is(TEST_ID));
    assertThat(eventDto.getActivityId(), is(TEST_ACTIVITY_ID));
  }

  private List<HistoricActivityInstanceEngineDto> setupInputData() {
    List<HistoricActivityInstanceEngineDto> resultList = new ArrayList<>();
    HistoricActivityInstanceEngineDto instance = new HistoricActivityInstanceEngineDto();
    instance.setId(TEST_ID);
    instance.setActivityId(TEST_ACTIVITY_ID);
    instance.setProcessDefinitionId("testProcessDefinition");
    resultList.add(instance);
    return resultList;
  }

  private void setupEngineEntityFetcher(List<HistoricActivityInstanceEngineDto> resultList) {
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}