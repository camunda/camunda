/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.operate.index.AbstractIndexDescriptor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MigrationRepositoryIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "migration-steps-repository";

  @Autowired private OperateProperties properties;

  public MigrationRepositoryIndex() {
    super(null, false);
  }

  @PostConstruct
  public void init() {
    indexPrefix = properties.getIndexPrefix(DatabaseInfo.getCurrent());
    isElasticsearch = DatabaseInfo.isElasticsearch();
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "1.1.0";
  }

  @Override
  public String getIndexPrefix() {
    return properties.getIndexPrefix();
  }
}
