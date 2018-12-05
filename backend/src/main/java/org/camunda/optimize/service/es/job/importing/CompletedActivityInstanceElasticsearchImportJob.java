package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.writer.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompletedActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(CompletedActivityInstanceElasticsearchImportJob.class);

  public CompletedActivityInstanceElasticsearchImportJob(CompletedActivityInstanceWriter completedActivityInstanceWriter) {
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      completedActivityInstanceWriter.importActivityInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("Error while writing completed activity instances to Elasticsearch", e);
    }
  }
}
