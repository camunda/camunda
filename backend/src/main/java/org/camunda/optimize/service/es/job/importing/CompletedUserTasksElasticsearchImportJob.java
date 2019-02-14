package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedUserTaskInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompletedUserTasksElasticsearchImportJob extends ElasticsearchImportJob<UserTaskInstanceDto> {
  private static final Logger logger = LoggerFactory.getLogger(CompletedUserTasksElasticsearchImportJob.class);

  private CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;

  public CompletedUserTasksElasticsearchImportJob(final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter) {
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
  }

  @Override
  protected void executeImport() {
    try {
      completedUserTaskInstanceWriter.importUserTaskInstances(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("Error while writing completed user task instances to elasticsearch", e);
    }
  }
}
