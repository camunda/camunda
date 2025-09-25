/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

public interface MigrationState {

  boolean shouldRunElementInstancePopulateProcessInstanceByDefinitionKey();

  /**
   * @return a string representing the version that applied migrations or null if the version is
   *     (not yet) set.
   */
  String getMigratedByVersion();
}
