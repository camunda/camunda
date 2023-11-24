/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

import java.util.List;

public class DecisionDefinitionXmlDatabaseImportJob extends DatabaseImportJob<DecisionDefinitionOptimizeDto> {

  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  public DecisionDefinitionXmlDatabaseImportJob(final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
                                                final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) {
    decisionDefinitionXmlWriter.importDecisionDefinitionXmls(newOptimizeEntities);
  }

}
