/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.clustervariable;

public enum ClusterVariableKind {
  JSON,
  SECRET_REFERENCE;

  public static ClusterVariableKind fromProtocol(
      final io.camunda.zeebe.protocol.record.value.ClusterVariableKind kind) {
    if (kind == null) {
      return JSON;
    }
    return switch (kind) {
      case JSON -> JSON;
      case SECRET_REFERENCE -> SECRET_REFERENCE;
    };
  }
}
