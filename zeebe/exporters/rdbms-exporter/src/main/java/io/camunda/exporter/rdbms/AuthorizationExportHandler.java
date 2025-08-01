/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.service.AuthorizationWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationExportHandler implements RdbmsExportHandler<AuthorizationRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationExportHandler.class);

  private final AuthorizationWriter authorizationWriter;

  public AuthorizationExportHandler(final AuthorizationWriter authorizationWriter) {
    this.authorizationWriter = authorizationWriter;
  }

  @Override
  public boolean canExport(final Record<AuthorizationRecordValue> record) {
    return record.getIntent() == AuthorizationIntent.CREATED
        || record.getIntent() == AuthorizationIntent.UPDATED
        || record.getIntent() == AuthorizationIntent.DELETED;
  }

  @Override
  public void export(final Record<AuthorizationRecordValue> record) {
    final AuthorizationRecordValue value = record.getValue();
    switch (record.getIntent()) {
      case AuthorizationIntent.CREATED -> authorizationWriter.createAuthorization(map(value));
      case AuthorizationIntent.UPDATED -> authorizationWriter.updateAuthorization(map(value));
      case AuthorizationIntent.DELETED -> authorizationWriter.deleteAuthorization(map(value));
      default -> LOG.warn("Unexpected intent {} for authorization record", record.getIntent());
    }
  }

  private AuthorizationDbModel map(final AuthorizationRecordValue authorization) {
    return new AuthorizationDbModel.Builder()
        .authorizationKey(authorization.getAuthorizationKey())
        .ownerId(authorization.getOwnerId())
        .ownerType(authorization.getOwnerType().name())
        .resourceType(authorization.getResourceType().name())
        .resourceMatcher(authorization.getResourceMatcher().value())
        .resourceId(authorization.getResourceId())
        .permissionTypes(authorization.getPermissionTypes())
        .build();
  }
}
