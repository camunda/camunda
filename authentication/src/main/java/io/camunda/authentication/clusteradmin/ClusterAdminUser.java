/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

/**
 * A cluster-admin user for HTTP Basic authentication against the {@code /cluster/v2/**} API.
 *
 * <p>Intentionally duplicated from {@code io.camunda.configuration.ClusterAdminUser} (configuration
 * module): {@code authentication} does not depend on {@code configuration} (see {@code
 * PhysicalTenantAuthConfigurations#VALID_TENANT_ID} for the same constraint on an analogous case),
 * so this module binds the {@code camunda.security.cluster-admin.basic.users} property path
 * independently. The configuration-module type exists for typed config metadata only; this one is
 * what actually gets bound and consumed. Keep the two in sync.
 *
 * @param name the login name, unique among configured cluster-admin users
 * @param password the plaintext password, encoded once at startup
 */
public record ClusterAdminUser(String name, String password) {}
