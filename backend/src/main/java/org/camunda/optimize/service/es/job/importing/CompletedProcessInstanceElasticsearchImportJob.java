package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompletedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(CompletedProcessInstanceElasticsearchImportJob.class);

  public CompletedProcessInstanceElasticsearchImportJob(CompletedProcessInstanceWriter completedProcessInstanceWriter) {
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      completedProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing completed process instances to elasticsearch", e);
    }
  }
}
