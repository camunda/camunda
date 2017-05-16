package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.ProcessInstanceWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceImportJob extends ImportJob<ProcessInstanceDto>{

  private ProcessInstanceWriter processInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(ProcessInstanceImportJob.class);

  public ProcessInstanceImportJob(ProcessInstanceWriter processInstanceWriter) {
    this.processInstanceWriter = processInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      processInstanceWriter.importProcessInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process instances to elasticsearch", e);
    }
  }
}
