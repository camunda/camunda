package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinishedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(FinishedProcessInstanceElasticsearchImportJob.class);

  public FinishedProcessInstanceElasticsearchImportJob(FinishedProcessInstanceWriter finishedProcessInstanceWriter) {
    this.finishedProcessInstanceWriter = finishedProcessInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      finishedProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing finished process instances to elasticsearch", e);
    }
  }
}
