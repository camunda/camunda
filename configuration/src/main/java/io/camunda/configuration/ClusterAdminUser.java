/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * A cluster-admin user for HTTP Basic authentication against the {@code /cluster/v2/**} API.
 * Cluster-admin users are configured statically here rather than persisted, since there is no
 * cluster-wide storage to hold them in.
 *
 * @param name the login name, unique among configured cluster-admin users
 * @param password the plaintext password, encoded once at startup
 */
public record ClusterAdminUser(String name, String password) {}
