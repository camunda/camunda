package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionImportJob;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ProcessDefinitionImportServiceTest {

  private static final String TEST_PROCESS_DEFINITION_ID = "testProcessdefinitionId";

  @InjectMocks
  private ProcessDefinitionImportService underTest;

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
  private MissingProcessDefinitionFinder missingProcessDefinitionFinder;


  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void executeImport() throws Exception {
    //given
    List<ProcessDefinitionEngineDto> resultList = setupInputData();
    setupEngineEntityFetcher(resultList);

    //when
    underTest.executeImport();

    //then
    ensureThatAJobWithCorrectIdWasCreated();
  }

  private void ensureThatAJobWithCorrectIdWasCreated() throws InterruptedException {
    ArgumentCaptor<ProcessDefinitionImportJob> procDefCaptor = ArgumentCaptor.forClass(ProcessDefinitionImportJob.class);
    verify(importJobExecutor).executeImportJob(procDefCaptor.capture());
    assertThat(procDefCaptor.getAllValues().size(), is(1));
    assertThat(procDefCaptor.getAllValues().get(0).getNewOptimizeEntities().size(), is(1));
    ProcessDefinitionOptimizeDto procDefDto = procDefCaptor.getAllValues().get(0).getNewOptimizeEntities().get(0);
    assertThat(procDefDto.getId(), is(TEST_PROCESS_DEFINITION_ID));
  }

  private List<ProcessDefinitionEngineDto> setupInputData() {
    List<ProcessDefinitionEngineDto> resultList = new ArrayList<>();
    ProcessDefinitionEngineDto instance = new ProcessDefinitionEngineDto();
    instance.setId(TEST_PROCESS_DEFINITION_ID);
    instance.setKey("testProcessDefinition");
    resultList.add(instance);
    return resultList;
  }

  private void setupEngineEntityFetcher(List<ProcessDefinitionEngineDto> resultList) {
    when(engineEntityFetcher.fetchProcessDefinitions(anyInt(), anyInt()))
      .thenReturn(resultList)
      .thenReturn(Collections.emptyList());
  }

}
