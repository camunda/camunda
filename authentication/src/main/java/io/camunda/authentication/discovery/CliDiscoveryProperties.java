/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.discovery;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the CLI login discovery ({@link CliAuthDiscoveryController}).
 *
 * @param displayName a human-readable name of the default tenant for that list; a physical tenant
 *     sets its own under {@code
 *     camunda.physical-tenants.<id>.security.authentication.cli-discovery}
 */
@NullMarked
@ConfigurationProperties(prefix = "camunda.security.authentication.cli-discovery")
public record CliDiscoveryProperties(@Nullable String displayName) {}
