/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.indices;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MigrationRepositoryIndex extends AbstractIndexDescriptor{

  public static final String INDEX_NAME = "migration-steps-repository";

  @Override
  public String getMainIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getIndexName() {
    return String.format("%s-%s", operateProperties.getElasticsearch().getIndexPrefix(), getMainIndexName());
  }

}
