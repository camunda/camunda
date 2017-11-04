package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.UnfinishedProcessInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnfinishedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(UnfinishedProcessInstanceElasticsearchImportJob.class);

  public UnfinishedProcessInstanceElasticsearchImportJob(UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter) {
    this.unfinishedProcessInstanceWriter = unfinishedProcessInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      unfinishedProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing unfinished process instances to elasticsearch", e);
    }
  }
}
