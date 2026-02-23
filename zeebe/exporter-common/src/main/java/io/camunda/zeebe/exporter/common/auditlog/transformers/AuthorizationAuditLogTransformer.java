/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;

public class AuthorizationAuditLogTransformer
    implements AuditLogTransformer<AuthorizationRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.AUTHORIZATION_CONFIG;
  }

  @Override
  public void transform(final Record<AuthorizationRecordValue> record, final AuditLogEntry log) {
    final AuthorizationRecordValue value = record.getValue();
    log.setRelatedEntityKey(value.getOwnerId());
    switch (value.getOwnerType()) {
      case USER -> log.setRelatedEntityType(AuditLogEntityType.USER);
      case CLIENT -> log.setRelatedEntityType(AuditLogEntityType.CLIENT);
      case GROUP -> log.setRelatedEntityType(AuditLogEntityType.GROUP);
      case ROLE -> log.setRelatedEntityType(AuditLogEntityType.ROLE);
      case MAPPING_RULE -> log.setRelatedEntityType(AuditLogEntityType.MAPPING_RULE);
      case TENANT -> log.setRelatedEntityType(AuditLogEntityType.TENANT);
      case null, default -> {
        // do nothing
      }
    }
  }
}
