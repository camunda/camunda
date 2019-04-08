/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateWriter;

import java.util.List;

public class VariableUpdateElasticsearchImportJob extends ElasticsearchImportJob<VariableDto> {

  private VariableUpdateWriter variableWriter;

  public VariableUpdateElasticsearchImportJob(VariableUpdateWriter variableWriter, Runnable callback) {
    super(callback);
    this.variableWriter = variableWriter;
  }

  @Override
  protected void persistEntities(List<VariableDto> newOptimizeEntities) throws Exception {
    variableWriter.importVariables(newOptimizeEntities);
  }

}
