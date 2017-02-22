package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProcessDefinitionImportJob implements ImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;
  private Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportJob.class);

    private List<ProcessDefinitionOptimizeDto> optimizeEntities;

  public ProcessDefinitionImportJob(ProcessDefinitionWriter processDefinitionWriter) {
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  public void addEntitiesToImport(List<ProcessDefinitionOptimizeDto> pageOfOptimizeEntities) {
    this.optimizeEntities = pageOfOptimizeEntities;
  }

  @Override
  public void fetchMissingEntityInformation() {
    // nothing to do here
  }

  @Override
  public void executeImport() {
    try {
      processDefinitionWriter.importProcessDefinitions(optimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
