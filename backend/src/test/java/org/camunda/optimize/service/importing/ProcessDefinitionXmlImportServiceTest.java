package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionXmlImportJob;
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ProcessDefinitionXmlImportServiceTest {

  private static final String TEST_PROCESS_DEFINITION_ID = "testProcessdefinitionId";

  @InjectMocks
  private ProcessDefinitionXmlImportService underTest;

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  @Mock
  private ProcessDefinitionWriter processDefinitionWriter;

  @Mock
  private ImportJobExecutor importJobExecutor;

  @Spy
  @Autowired
  private ConfigurationService configurationService;

  @Spy
  @Autowired
  private MissingProcessDefinitionXmlFinder missingProcessDefinitionXmlFinder;

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
    ensureThatAJobWithCorrectIdWasCreated();
  }

  private List<ProcessDefinitionXmlEngineDto> setupInputData() {
    List<ProcessDefinitionXmlEngineDto> list = new ArrayList<>();
    ProcessDefinitionXmlEngineDto instance = new ProcessDefinitionXmlEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    list.add(instance);
    return list;
  }

  private void ensureThatAJobWithCorrectIdWasCreated() throws InterruptedException {
    ArgumentCaptor<ProcessDefinitionXmlImportJob> procDefXmlCaptor = ArgumentCaptor.forClass(ProcessDefinitionXmlImportJob.class);
    verify(importJobExecutor, atLeast(1)).executeImportJob(procDefXmlCaptor.capture());
    assertThat(procDefXmlCaptor.getAllValues().get(0).getEntitiesToImport().size(), is(1));
    ProcessDefinitionXmlOptimizeDto procDefXmlDto = procDefXmlCaptor.getAllValues().get(0).getEntitiesToImport().get(0);
    assertThat(procDefXmlDto.getId(), is(TEST_PROCESS_DEFINITION_ID));
  }

  private void setupEngineEntityFetcher(List<ProcessDefinitionXmlEngineDto> resultList) {
    when(engineEntityFetcher.fetchProcessDefinitionXmls(anyInt(), anyInt()))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}
