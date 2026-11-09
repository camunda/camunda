/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Holds the client used for read queries. It is not a {@link CamundaClient} bean because a second
 * one would be ambiguous for the default client, and this branch has no named clients. It shares
 * the executor service of the configuration and shuts it down on close, so the starter must not run
 * in the same JVM as job workers.
 */
@Component
@Lazy
@Profile("starter")
public class QueryClient implements AutoCloseable {

  private final CamundaClient client;

  public QueryClient(final CamundaClientConfiguration clientConfiguration) {
    client = CamundaClient.newClient(clientConfiguration);
  }

  public CamundaClient client() {
    return client;
  }

  @Override
  public void close() {
    client.close();
  }
}
