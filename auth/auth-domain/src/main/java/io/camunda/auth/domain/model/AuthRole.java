/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

/** A role that can be assigned to users, groups, or mapping rules. */
public record AuthRole(
    long roleKey,
    String roleId,
    String name,
    String description) {}
