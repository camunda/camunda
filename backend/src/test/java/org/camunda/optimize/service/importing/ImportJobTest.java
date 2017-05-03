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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ImportJobTest {

  @Mock
  private EventsWriter eventsWriter;

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
    ImportJob importJob = new EventImportJob(eventsWriter);

    // when
    importJob.run();

    // then
    verify(eventsWriter, times(1)).importEvents(any());
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
