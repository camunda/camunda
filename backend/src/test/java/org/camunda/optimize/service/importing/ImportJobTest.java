package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.camunda.optimize.service.importing.job.impl.EventImportJob;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionImportJob;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionXmlImportJob;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ImportJobTest {

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
    EventsWriter eventsWriter = mock(EventsWriter.class);
    ImportJob importJob = new EventImportJob(eventsWriter);

    // when
    importJob.run();

    // then
    verify(eventsWriter, times(1)).importEvents(any());
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
