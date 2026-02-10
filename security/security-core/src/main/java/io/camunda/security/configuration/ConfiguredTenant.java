/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.List;

public record ConfiguredTenant(
    String tenantId,
    String name,
    String description,
    List<String> users,
    List<String> clients,
    List<String> roles,
    List<String> groups,
    List<String> mappingRules) {}
