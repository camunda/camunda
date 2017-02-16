package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ProcessDefinitionImportServiceTest {

  private static final String TEST_PROCESS_DEFINITION_ID = "testProcessdefinitionId";
  private static final String ENGINE_TARGET = "http://localhost:8080/engine-rest/engine/default";

  @InjectMocks
  @Autowired
  private ProcessDefinitionImportService underTest;

  @InjectMocks
  @Autowired
  private MissingProcessDefinitionFinder missingProcessDefinitionFinder;

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
    List<ProcessDefinitionEngineDto> resultList = setupInputData();

    setupClient(resultList);

    //when
    underTest.executeImport();

    //then
    //verify invocations
    Mockito.verify(clientMock, Mockito.times(2))
      .target(Mockito.anyString());

    Mockito.verify(processDefinitionWriter, Mockito.times(1))
      .importProcessDefinitions(Mockito.argThat(matchesEvent()));
  }

  private List<ProcessDefinitionEngineDto> setupInputData() {
    List<ProcessDefinitionEngineDto> resultList = new ArrayList<>();
    ProcessDefinitionEngineDto instance = new ProcessDefinitionEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    instance.setKey("testProcessDefinition");
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
          result = cast.size() == 1 && TEST_PROCESS_DEFINITION_ID.equals(((ProcessDefinitionOptimizeDto) cast.get(0)).getId());

        }
        return result;
      }
    };
  }


  private void setupClient(List<ProcessDefinitionEngineDto> resultList) {
    WebTarget mockTarget = mock(WebTarget.class);
    Invocation.Builder builderMock = mock(Invocation.Builder.class);
    when(clientMock.target(eq(ENGINE_TARGET))).thenReturn(mockTarget);
    when(mockTarget.path(Mockito.anyString())).thenReturn(mockTarget);
    when(mockTarget.queryParam(anyString(), any())).thenReturn(mockTarget);
    when(mockTarget.request(Mockito.anyString())).thenReturn(builderMock);
    when(builderMock.get(eq(new GenericType<List<ProcessDefinitionEngineDto>>() {
    })))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}