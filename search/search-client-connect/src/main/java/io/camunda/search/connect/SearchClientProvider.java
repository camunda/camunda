/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.util.function.BiFunction;

public interface SearchClientProvider
    extends BiFunction<ConnectConfiguration, ObjectMapper, CamundaSearchClient> {}
