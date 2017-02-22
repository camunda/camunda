package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProcessDefinitionXmlImportJob extends ImportJob<ProcessDefinitionXmlOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;
  private Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlImportJob.class);

    private List<ProcessDefinitionXmlOptimizeDto> optimizeEntities;

  public ProcessDefinitionXmlImportJob(ProcessDefinitionWriter processDefinitionWriter) {
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  public void addEntitiesToImport(List<ProcessDefinitionXmlOptimizeDto> pageOfOptimizeEntities) {
    this.optimizeEntities = pageOfOptimizeEntities;
  }

  @Override
  protected void fetchMissingEntityInformation() {
    // nothing to do here
  }

  @Override
  protected void executeImport() {
    try {
      processDefinitionWriter.importProcessDefinitionXmls(optimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
