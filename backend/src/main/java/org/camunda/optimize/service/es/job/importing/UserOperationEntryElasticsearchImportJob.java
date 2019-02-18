package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.UserOperationsLogEntryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserOperationEntryElasticsearchImportJob extends ElasticsearchImportJob<UserOperationLogEntryDto> {
  private static final Logger logger = LoggerFactory.getLogger(UserOperationEntryElasticsearchImportJob.class);

  private UserOperationsLogEntryWriter userOperationsLogEntryWriter;

  public UserOperationEntryElasticsearchImportJob(final UserOperationsLogEntryWriter userOperationsLogEntryWriter) {
    this.userOperationsLogEntryWriter = userOperationsLogEntryWriter;
  }

  @Override
  protected void executeImport() {
    try {
      userOperationsLogEntryWriter.importUserOperationLogEntries(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("Error while writing completed user task instances to elasticsearch", e);
    }
  }
}
