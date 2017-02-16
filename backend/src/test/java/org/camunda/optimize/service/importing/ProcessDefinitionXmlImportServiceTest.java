package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

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
    verify(clientMock, times(3))
      .target(anyString());

    verify(processDefinitionWriter, times(1))
      .importProcessDefinitionXmls(argThat(matchesEvent()));
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
    WebTarget mockTarget = mock(WebTarget.class);
    Invocation.Builder builderMock = mock(Invocation.Builder.class);
    when(clientMock.target(eq(ENGINE_TARGET))).thenReturn(mockTarget);
    when(mockTarget.path(anyString())).thenReturn(mockTarget);
    when(mockTarget.queryParam(anyString(), any())).thenReturn(mockTarget);
    when(mockTarget.request(anyString())).thenReturn(builderMock);
    when(builderMock.get(eq(new GenericType<List<ProcessDefinitionEngineDto>>() {
    })))
      .thenReturn(procDefList)
      .thenReturn(Collections.emptyList());
    when(builderMock.get(eq(new GenericType<ProcessDefinitionXmlEngineDto>() {
    })))
      .thenReturn(xml);
  }

  private List<ProcessDefinitionEngineDto> setupProcessDefinitionInputData() {
    List<ProcessDefinitionEngineDto> resultList = new ArrayList<>();
    ProcessDefinitionEngineDto instance = new ProcessDefinitionEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    resultList.add(instance);
    return resultList;
  }

}