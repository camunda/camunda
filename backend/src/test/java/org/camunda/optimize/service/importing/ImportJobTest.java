package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.camunda.optimize.service.importing.job.impl.EventImportJob;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionImportJob;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionXmlImportJob;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ImportJobTest {

  @Mock
  private EventsWriter eventsWriter;

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  private static String PROCESS_INSTANCE_ID = "procInstId";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void processDefinitionImportJobIsExecuted() throws Exception {
    // given
    ProcessDefinitionWriter processDefinitionWriter = mock(ProcessDefinitionWriter.class);
    ImportJob importJob = new ProcessDefinitionImportJob(processDefinitionWriter);

    // when
    importJob.run();

    // then
    verify(processDefinitionWriter, times(1)).importProcessDefinitions(any());
  }

  @Test
  public void eventImportJobIsExecuted() throws Exception {
    // given
    EngineEntityFetcher engineEntityFetcher = mock(EngineEntityFetcher.class);
    ImportJob importJob = new EventImportJob(engineEntityFetcher, eventsWriter);

    // when
    importJob.run();

    // then
    verify(eventsWriter, times(1)).importEvents(any());
  }

  @Test
  public void eventImportCorrectlyFetchesAggregateInformation() throws Exception {
    // given
    EventImportJob importJob = new EventImportJob(engineEntityFetcher, eventsWriter);
    List<EventDto> entitiesToImport = createImportEntities();
    importJob.setEntitiesToImport(entitiesToImport);

    HistoricProcessInstanceDto procInst = createMissingAggregateInformation();
//    String[] ids = {PROCESS_INSTANCE_ID};
    Set<String> ids = new HashSet<>();
    ids.add(PROCESS_INSTANCE_ID);
    when(engineEntityFetcher.fetchHistoricProcessInstances(ids)).thenReturn(Collections.singletonList(procInst));

    // when
    importJob.run();

    // then
    ArgumentCaptor<List<EventDto>> eventCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventsWriter, times(1)).importEvents(eventCaptor.capture());
    EventDto result = eventCaptor.getValue().get(0);
    assertThat(result.getProcessInstanceStartDate(), is(procInst.getStartTime()));
    assertThat(result.getProcessInstanceEndDate(), is(procInst.getEndTime()));
  }

  private HistoricProcessInstanceDto createMissingAggregateInformation() {
    HistoricProcessInstanceDto dto = new HistoricProcessInstanceDto();
    dto.setStartTime(new Date() );
    dto.setEndTime(new Date());
    dto.setId(PROCESS_INSTANCE_ID);
    return dto;
  }

  private List<EventDto> createImportEntities() {
    EventDto eventDto = new EventDto();
    eventDto.setId("123");
    eventDto.setProcessInstanceId(PROCESS_INSTANCE_ID);
    List<EventDto> list = new ArrayList<>();
    list.add(eventDto);
    return list;
  }

  @Test
  public void processDefinitionXmlImportJobIsExecuted() throws Exception {
    // given
    ProcessDefinitionWriter processDefinitionWriter = mock(ProcessDefinitionWriter.class);
    ImportJob importJob = new ProcessDefinitionXmlImportJob(processDefinitionWriter);

    // when
    importJob.run();

    // then
    verify(processDefinitionWriter, times(1)).importProcessDefinitionXmls(any());
  }
}
