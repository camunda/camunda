package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import java.util.ArrayList;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ProcessDefinitionXmlImportServiceTest {

  private static final String TEST_PROCESS_DEFINITION_ID = "testProcessdefinitionId";
  private static final String ENGINE_TARGET = "http://localhost:8080/engine-rest/engine/default";

  @InjectMocks
  @Autowired
  private ProcessDefinitionXmlImportService underTest;

  @InjectMocks
  @Autowired
  private MissingProcessDefinitionXmlFinder missingProcessDefinitionXmlFinder;

  @Mock
  private Client clientMock;

  @Mock
  private ProcessDefinitionWriter processDefinitionWriter;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void executeImport() throws Exception {
    //given
    ProcessDefinitionXmlEngineDto xml = setupInputData();

    setupClient(xml);

    //when
    underTest.executeImport();

    //then
    //verify invocations
    Mockito.verify(clientMock, Mockito.times(2))
      .target(Mockito.anyString());

    Mockito.verify(processDefinitionWriter, Mockito.times(1))
      .importProcessDefinitionXmls(Mockito.argThat(matchesEvent()));
  }

  private ProcessDefinitionXmlEngineDto setupInputData() {
    ProcessDefinitionXmlEngineDto instance = new ProcessDefinitionXmlEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    return instance;
  }

  private <Object> ArgumentMatcher<Object> matchesEvent() {
    return new ArgumentMatcher<Object>() {
      @Override
      public boolean matches(Object t) {
        boolean result = false;

        if (t instanceof List) {
          List cast = (List) t;
          result = cast.size() == 1 && TEST_PROCESS_DEFINITION_ID.equals(((ProcessDefinitionXmlOptimizeDto) cast.get(0)).getId());

        }
        return result;
      }
    };
  }


  private void setupClient(ProcessDefinitionXmlEngineDto xml) {
    List<ProcessDefinitionEngineDto> procDefList = setupProcessDefinitionInputData();
    WebTarget mockTarget = Mockito.mock(WebTarget.class);
    Mockito.when(mockTarget.path(Mockito.anyString())).thenReturn(mockTarget);
    Invocation.Builder builderMock = Mockito.mock(Invocation.Builder.class);
    Mockito.when(builderMock.get(Mockito.eq(new GenericType<List<ProcessDefinitionEngineDto>>() {
    })))
      .thenReturn(procDefList);
    Mockito.when(builderMock.get(Mockito.eq(new GenericType<ProcessDefinitionXmlEngineDto>() {
    })))
      .thenReturn(xml);
    Mockito.when(mockTarget.request(Mockito.anyString())).thenReturn(builderMock);
    Mockito.when(clientMock.target(Mockito.eq(ENGINE_TARGET))).thenReturn(mockTarget);
  }

  private List<ProcessDefinitionEngineDto> setupProcessDefinitionInputData() {
    List<ProcessDefinitionEngineDto> resultList = new ArrayList<>();
    ProcessDefinitionEngineDto instance = new ProcessDefinitionEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    resultList.add(instance);
    return resultList;
  }

}