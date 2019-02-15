package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProcessDefinitionElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionElasticsearchImportJob(ProcessDefinitionWriter processDefinitionWriter) {
    super(() -> {});
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) throws Exception {
    processDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
