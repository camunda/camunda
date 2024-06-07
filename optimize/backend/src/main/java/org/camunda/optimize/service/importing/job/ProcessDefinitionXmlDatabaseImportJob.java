/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

public class ProcessDefinitionXmlDatabaseImportJob
    extends DatabaseImportJob<ProcessDefinitionOptimizeDto> {

  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlDatabaseImportJob(
      final ProcessDefinitionXmlWriter processDefinitionXmlWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) {
    processDefinitionXmlWriter.importProcessDefinitionXmls(newOptimizeEntities);
  }
}
