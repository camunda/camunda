/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.util.FeatureFlags;

/** The collaborators the broker provides to the engine of a partition. */
public record EngineDependencies(
    int partitionCount,
    FeatureFlags featureFlags,
    JobStreamer jobStreamer,
    SearchClientsProxy searchClientsProxy,
    BrokerRequestAuthorizationConverter authorizationConverter,
    SecretStoreRegistry secretStoreRegistry) {}
