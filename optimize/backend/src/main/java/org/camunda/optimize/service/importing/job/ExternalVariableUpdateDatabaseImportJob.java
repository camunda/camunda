/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

public class ExternalVariableUpdateDatabaseImportJob extends DatabaseImportJob<ProcessVariableDto> {

  private final ProcessVariableUpdateWriter variableWriter;
  private final ConfigurationService configurationService;

  public ExternalVariableUpdateDatabaseImportJob(
      final ProcessVariableUpdateWriter variableWriter,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessVariableDto> variableUpdates) {
    databaseClient.executeImportRequestsAsBulk(
        "External variable updates",
        variableWriter.generateVariableUpdateImports(variableUpdates),
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
