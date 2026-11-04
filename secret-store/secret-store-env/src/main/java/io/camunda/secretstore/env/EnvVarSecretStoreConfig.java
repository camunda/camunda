/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import java.util.Map;

/**
 * Configuration of an {@link EnvVarSecretStore}.
 *
 * @param prefix the prefix every secret variable carries; must not be blank, malformed, or start
 *     with a reserved prefix, see {@link EnvVarSecretStore}
 * @param environment the variables the store snapshots, normally {@link System#getenv()}
 */
public record EnvVarSecretStoreConfig(String prefix, Map<String, String> environment) {}
