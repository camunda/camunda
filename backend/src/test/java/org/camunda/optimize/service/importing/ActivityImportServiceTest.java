package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ActivityImportServiceTest {

  private static final String TEST_ACTIVITY_ID = "testActivityId";
  private static final String TEST_ID = "testId";

  @InjectMocks
  @Autowired
  private ActivityImportService underTest;

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  @Mock
  private EventsWriter eventsWriter;

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
    //verify invocations
    verify(engineEntityFetcher, atLeast(1))
      .fetchHistoricActivityInstances(anyInt(), anyInt());

    verify(eventsWriter, times(1))
      .importEvents(argThat(matchesEvent()));
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

  private <Object> ArgumentMatcher<Object> matchesEvent() {
    return new ArgumentMatcher<Object>() {
      @Override
      public boolean matches(Object t) {
        boolean result = false;

        if (t instanceof List) {
          List cast = (List) t;
          result = cast.size() == 1 && TEST_ACTIVITY_ID.equals(((EventDto) cast.get(0)).getActivityId());

        }
        return result;
      }
    };
  }

  private void setupEngineEntityFetcher(List<HistoricActivityInstanceEngineDto> resultList) {
    when(engineEntityFetcher.fetchHistoricActivityInstances(anyInt(), anyInt()))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}