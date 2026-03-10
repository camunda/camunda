/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Monorepo-specific SPI implementations for the {@code auth} library.
 *
 * <p>The auth library ({@code auth/}) defines its domain through SPIs (Service Provider Interfaces)
 * in {@code io.camunda.auth.domain.spi}. It ships with no-op or claim-based defaults (e.g., {@code
 * NoOpMembershipResolver}) that are sufficient for standalone deployments. Inside the Camunda
 * monorepo, however, these defaults must be replaced with implementations that delegate to the
 * monorepo's own service layer ({@code io.camunda.service.*}) and search infrastructure.
 *
 * <p>This package provides those concrete implementations. They live in {@code dist/} because they
 * depend on monorepo-internal classes that the auth library must not know about:
 *
 * <ul>
 *   <li>{@link io.camunda.application.commons.authentication.CamundaMembershipResolver} &mdash;
 *       resolves OIDC token claims into groups, roles, and tenants via {@code MappingRuleServices},
 *       {@code GroupServices}, {@code RoleServices}, and {@code TenantServices}.
 *   <li>{@link io.camunda.application.commons.authentication.CamundaNoDBMembershipResolver} &mdash;
 *       fallback when no secondary storage is available; extracts groups from JWT claims only.
 *   <li>{@link io.camunda.application.commons.authentication.CamundaBasicAuthMembershipResolver}
 *       &mdash; resolves memberships for locally-authenticated (basic-auth) users via the same
 *       service layer.
 *   <li>{@link io.camunda.application.commons.authentication.CamundaAdminUserCheckProvider} &mdash;
 *       checks whether an admin user exists, using {@code RoleServices} and {@code
 *       SecurityConfiguration}.
 *   <li>{@link io.camunda.application.commons.authentication.CamundaWebComponentAccessProvider}
 *       &mdash; checks component-level authorization via {@code ResourceAccessProvider}.
 *   <li>{@link io.camunda.application.commons.authentication.CamundaUserProfileProvider} &mdash;
 *       looks up user display name and email via {@code UserServices}.
 * </ul>
 *
 * <p>These beans are registered by {@link
 * io.camunda.application.commons.authentication.CamundaAuthSdkConfiguration} and take precedence
 * over the auth library's defaults via Spring's {@code @ConditionalOnMissingBean} mechanism.
 *
 * @see io.camunda.auth.domain.spi
 */
package io.camunda.application.commons.authentication;
