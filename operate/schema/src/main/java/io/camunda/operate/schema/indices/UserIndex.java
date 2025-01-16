/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.indices;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserIndex extends AbstractIndexDescriptor implements Prio5Backup {

  public static final String INDEX_NAME = "user";
  public static final String ID = "id";
  public static final String USER_ID = "userId";
  public static final String PASSWORD = "password";
  public static final String ROLES = "roles";
  public static final String DISPLAY_NAME = "displayName";

  @Autowired private OperateProperties properties;

  public UserIndex() {
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
    return "1.2.0";
  }

  @Override
  public String getIndexPrefix() {
    return properties.getIndexPrefix();
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
