package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ProcessDefinitionXmlImportServiceTest {

  private static final String TEST_PROCESS_DEFINITION_ID = "testProcessdefinitionId";

  @InjectMocks
  @Autowired
  private ProcessDefinitionXmlImportService underTest;

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  @Mock
  private ProcessDefinitionWriter processDefinitionWriter;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void executeImport() throws Exception {
    //given
    List<ProcessDefinitionXmlEngineDto> xmls = setupInputData();
    setupEngineEntityFetcher(xmls);

    //when
    underTest.executeImport();

    //then
    //verify invocations
    verify(engineEntityFetcher, atLeast(1))
      .fetchProcessDefinitionXmls(anyInt(), anyInt());

    verify(processDefinitionWriter, times(1))
      .importProcessDefinitionXmls(argThat(matchesEvent()));
  }

  private List<ProcessDefinitionXmlEngineDto> setupInputData() {
    List<ProcessDefinitionXmlEngineDto> list = new ArrayList<>();
    ProcessDefinitionXmlEngineDto instance = new ProcessDefinitionXmlEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    list.add(instance);
    return list;
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

  private void setupEngineEntityFetcher(List<ProcessDefinitionXmlEngineDto> resultList) {
    when(engineEntityFetcher.fetchProcessDefinitionXmls(anyInt(), anyInt()))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}