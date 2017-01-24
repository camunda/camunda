package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.EventsWriter;
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
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ActivityImportServiceTest {

  public static final String TEST_ACTIVITY_ID = "testActivityId";
  @InjectMocks
  @Autowired
  private ActivityImportService underTest;

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
    List<HistoricActivityInstanceDto> resultList = setupInputData();

    setupClient(resultList);

    //when
    underTest.executeImport();

    //then
    //verify invocations
    Mockito.verify(clientMock,Mockito.times(1))
        .target(Mockito.anyString());

    Mockito.verify(eventsWriter, Mockito.times(1))
        .importEvents(Mockito.argThat(matchesEvent()));
  }

  private List<HistoricActivityInstanceDto> setupInputData() {
    List<HistoricActivityInstanceDto> resultList = new ArrayList<>();
    HistoricActivityInstanceDto instance = new HistoricActivityInstanceDto();
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


  private void setupClient(List<HistoricActivityInstanceDto> resultList) {
    WebTarget mockTarget = Mockito.mock(WebTarget.class);
    Mockito.when(mockTarget.path(Mockito.anyString())).thenReturn(mockTarget);
    Invocation.Builder builderMock = Mockito.mock(Invocation.Builder.class);
    Mockito.when(builderMock.get(Mockito.eq(new GenericType<List<HistoricActivityInstanceDto>>() {})))
        .thenReturn(resultList);
    Mockito.when(mockTarget.request(Mockito.anyString())).thenReturn(builderMock);
    Mockito.when(clientMock.target(Mockito.anyString())).thenReturn(mockTarget);
  }

}