/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.entities.auditlog.AuditLogJoinRelationshipType;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.EntityJoinRelationFactory;

public class AuditLogIndex extends AbstractIndexDescriptor implements Prio5Backup {

  public static final String INDEX_NAME = "audit-log";
  public static final String INDEX_VERSION = "8.9.0";
  public static final String ENTITY_KEY = "entityKey";

  public static final EntityJoinRelationFactory<AuditLogJoinRelationshipType>
      JOIN_RELATION_FACTORY =
          new EntityJoinRelationFactory<>(
              AuditLogJoinRelationshipType.BATCH_OPERATION,
              AuditLogJoinRelationshipType.BATCH_ITEM);

  public AuditLogIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
  }
}
