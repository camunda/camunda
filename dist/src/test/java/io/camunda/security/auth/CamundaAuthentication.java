/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Test-only stub at the old FQN used to produce byte[] payloads that simulate sessions serialized
 * before the CamundaAuthentication class was migrated to CSL.
 */
public record CamundaAuthentication(
    String authenticatedUsername,
    String authenticatedClientId,
    boolean anonymousUser,
    List<String> authenticatedGroupIds,
    List<String> authenticatedRoleIds,
    List<String> authenticatedTenantIds,
    List<String> authenticatedMappingRuleIds,
    Map<String, Object> claims)
    implements Serializable {}
