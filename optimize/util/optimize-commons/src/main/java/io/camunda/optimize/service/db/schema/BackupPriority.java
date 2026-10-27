/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

/**
 * Determines which of the two backup snapshot requests an index is captured in. {@code PRIORITY1}
 * indices are snapshotted first and must complete before {@code PRIORITY2} indices are snapshotted,
 * so that indices describing the state of other indices are never captured in a state newer than
 * the data they describe.
 */
public enum BackupPriority {
  PRIORITY1,
  PRIORITY2
}
