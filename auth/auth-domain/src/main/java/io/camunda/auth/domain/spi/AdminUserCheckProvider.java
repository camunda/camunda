/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.spi;

/** Abstracts the check for whether at least one admin user exists. */
public interface AdminUserCheckProvider {
  /** Returns true if at least one admin user exists (configured or in DB). */
  boolean hasAdminUser();
}
