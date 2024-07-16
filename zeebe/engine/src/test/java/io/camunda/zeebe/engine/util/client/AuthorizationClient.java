/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import java.util.List;

public class AuthorizationClient {
  private final AuthorizationRecord authorizationRecord;
  private final CommandWriter writer;

  public AuthorizationClient(final CommandWriter writer) {
    this.writer = writer;
    authorizationRecord = new AuthorizationRecord();
  }

  public AuthorizationCreationClient newAuthorization(final String username) {
    return new AuthorizationCreationClient(writer, username);
  }

  public static class AuthorizationCreationClient {

    private final CommandWriter writer;
    private final AuthorizationRecord authorizationRecord;

    public AuthorizationCreationClient(final CommandWriter writer, final String username) {
      this.writer = writer;
      authorizationRecord = new AuthorizationRecord();
      authorizationRecord.setUsername(username);
    }

    public AuthorizationCreationClient withResourceKey(final String resourceKey) {
      authorizationRecord.setResourceKey(resourceKey);
      return this;
    }

    public AuthorizationCreationClient withResourceType(final String resourceType) {
      authorizationRecord.setResourceType(resourceType);
      return this;
    }

    public AuthorizationCreationClient withPermissions(final List<String> permissions) {
      authorizationRecord.setPermissions(permissions);
      return this;
    }

    public AuthorizationRecord create() {
      final long position = writer.writeCommand(AuthorizationIntent.CREATE, authorizationRecord);
      return null;
    }
  }
}
