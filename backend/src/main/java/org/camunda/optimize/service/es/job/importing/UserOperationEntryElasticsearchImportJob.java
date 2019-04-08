/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.UserOperationsLogEntryWriter;

import java.util.List;

public class UserOperationEntryElasticsearchImportJob extends ElasticsearchImportJob<UserOperationLogEntryDto> {

  private UserOperationsLogEntryWriter userOperationsLogEntryWriter;

  public UserOperationEntryElasticsearchImportJob(final UserOperationsLogEntryWriter userOperationsLogEntryWriter,
                                                  Runnable callback) {
    super(callback);
    this.userOperationsLogEntryWriter = userOperationsLogEntryWriter;
  }

  @Override
  protected void persistEntities(List<UserOperationLogEntryDto> newOptimizeEntities) throws Exception {
    userOperationsLogEntryWriter.importUserOperationLogEntries(newOptimizeEntities);
  }
}
