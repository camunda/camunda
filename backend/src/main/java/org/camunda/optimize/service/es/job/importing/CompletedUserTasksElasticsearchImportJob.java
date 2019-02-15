package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedUserTaskInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CompletedUserTasksElasticsearchImportJob extends ElasticsearchImportJob<UserTaskInstanceDto> {
  private CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;

  public CompletedUserTasksElasticsearchImportJob(final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter,
                                                  Runnable callback) {
    super(callback);
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
  }

  @Override
  protected void persistEntities(List<UserTaskInstanceDto> newOptimizeEntities) throws Exception {
    completedUserTaskInstanceWriter.importUserTaskInstances(newOptimizeEntities);
  }
}
