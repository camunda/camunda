package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionImportJob extends ImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;
  private Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportJob.class);

  public ProcessDefinitionImportJob(ProcessDefinitionWriter processDefinitionWriter) {
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void fetchMissingEntityInformation() {
    // nothing to do here
  }

  @Override
  protected void executeImport() {
    try {
      processDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
