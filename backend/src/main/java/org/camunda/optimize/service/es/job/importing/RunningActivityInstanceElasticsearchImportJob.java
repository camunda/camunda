package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunningActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private RunningActivityInstanceWriter runningActivityInstanceWriter;
  private Logger logger = LoggerFactory.getLogger(RunningActivityInstanceElasticsearchImportJob.class);

  public RunningActivityInstanceElasticsearchImportJob(RunningActivityInstanceWriter runningActivityInstanceWriter) {
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      runningActivityInstanceWriter.importActivityInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing running activity instances to Elasticsearch", e);
    }
  }
}
