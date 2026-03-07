/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import io.camunda.auth.domain.auth.MappingRuleMatcher;

/**
 * An OIDC claim-to-identity mapping rule. When a JWT claim matches the rule's claimName/claimValue,
 * the associated roles/groups/tenants are assigned to the principal.
 */
public record AuthMappingRule(
    long mappingRuleKey, String mappingRuleId, String claimName, String claimValue, String name)
    implements MappingRuleMatcher.MappingRule {}
