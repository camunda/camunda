/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.schema.indices;

public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  protected String indexPrefix;

  public AbstractIndexDescriptor(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  public String getFullQualifiedName() {
    // if(DatabaseInfo.isElasticsearch()) {
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
    // } else{
    // return String.format("%s-%s-%s_", operateProperties.getOpensearch().getIndexPrefix(),
    // getIndexName(), getVersion());
    // }
  }
}
