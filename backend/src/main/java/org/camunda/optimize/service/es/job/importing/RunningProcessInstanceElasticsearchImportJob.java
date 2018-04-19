package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunningProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(RunningProcessInstanceElasticsearchImportJob.class);

  public RunningProcessInstanceElasticsearchImportJob(RunningProcessInstanceWriter runningProcessInstanceWriter) {
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      runningProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing unfinished process instances to elasticsearch", e);
    }
  }
}
