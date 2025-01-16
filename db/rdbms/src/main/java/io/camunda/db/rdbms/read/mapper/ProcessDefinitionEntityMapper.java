/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.search.entities.ProcessDefinitionEntity;

public class ProcessDefinitionEntityMapper {

  public static ProcessDefinitionEntity toEntity(final ProcessDefinitionDbModel dbModel) {
    return new ProcessDefinitionEntity(
        dbModel.processDefinitionKey(),
        dbModel.name(),
        dbModel.processDefinitionId(),
        dbModel.bpmnXml(),
        dbModel.resourceName(),
        dbModel.version(),
        dbModel.versionTag(),
        dbModel.tenantId(),
        dbModel.formId()
    );
  }
}
