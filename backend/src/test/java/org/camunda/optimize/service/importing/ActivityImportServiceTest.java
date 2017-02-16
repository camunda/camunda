package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ActivityImportServiceTest {

  private static final String TEST_ACTIVITY_ID = "testActivityId";
  private static final String TEST_ID = "testId";
  private static final String ENGINE_TARGET = "http://localhost:8080/engine-rest/engine/default";

  @InjectMocks
  @Autowired
  private ActivityImportService underTest;

  @InjectMocks
  @Autowired
  private MissingActivityFinder missingActivityFinder;

  @Mock
  private Client clientMock;

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

    setupEngineClient(resultList);

    //when
    underTest.executeImport();

    //then
    //verify invocations
    verify(clientMock, times(2))
        .target(anyString());

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
          result = cast.size() == 1 && TEST_ACTIVITY_ID.equals(((EventDto)cast.get(0)).getActivityId());

        }
        return result;
      }
    };
  }


  private void setupEngineClient(List<HistoricActivityInstanceEngineDto> resultList) {
    WebTarget mockTarget = mock(WebTarget.class);
    Invocation.Builder builderMock = mock(Invocation.Builder.class);
    when(clientMock.target(eq(ENGINE_TARGET))).thenReturn(mockTarget);
    when(mockTarget.path(Mockito.anyString())).thenReturn(mockTarget);
    when(mockTarget.queryParam(anyString(), any())).thenReturn(mockTarget);
    when(mockTarget.request(Mockito.anyString())).thenReturn(builderMock);
    when(builderMock.get(eq(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
    })))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}