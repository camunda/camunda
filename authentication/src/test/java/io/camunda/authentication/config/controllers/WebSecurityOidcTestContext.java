/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import org.springframework.context.annotation.Configuration;

/**
 * Marker context for OIDC-specific test setup. The shared beans (RoleServices, GroupServices,
 * TenantServices, MappingRuleServices, fallback MembershipService) are provided by {@link
 * WebSecurityConfigTestContext}; OIDC tests load both contexts.
 */
@Configuration
public class WebSecurityOidcTestContext {}
