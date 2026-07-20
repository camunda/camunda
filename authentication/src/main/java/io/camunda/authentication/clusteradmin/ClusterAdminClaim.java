/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

/**
 * A generic OIDC access-token claim matcher for cluster-admin authorization: a client is granted
 * cluster-admin access when the token's claim {@code name} matches {@code value}.
 *
 * <p>Intentionally duplicated from {@code io.camunda.configuration.ClusterAdminClaim} — {@code
 * authentication} cannot depend on {@code configuration} (see {@code ClusterAdminUser} for the same
 * constraint), so this module binds {@code camunda.security.cluster-admin.oidc.claims}
 * independently. Keep the two in sync.
 *
 * @param name the claim name (a JSONPath into the token claims)
 * @param value the value the claim must equal (scalar) or contain (list)
 */
public record ClusterAdminClaim(String name, String value) {}
