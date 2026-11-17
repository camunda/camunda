/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * A secret store that reads secret {@code name} from the environment variable {@code
 * <prefix><name>}, snapshotted once at startup. See {@link
 * io.camunda.secretstore.env.EnvVarSecretStore} for how names are matched and which are refused.
 */
@NullMarked
package io.camunda.secretstore.env;

import org.jspecify.annotations.NullMarked;
