/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.Properties;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class ClientRule extends ExternalResource {

  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(2);
  protected final Supplier<Properties> properties;
  protected ZeebeClient client;

  public ClientRule() {
    this(Properties::new);
  }

  public ClientRule(final Supplier<Properties> propertiesProvider) {
    properties = propertiesProvider;
  }

  public ZeebeClient getClient() {
    return client;
  }

  @Override
  protected void before() {
    createClient();
  }

  @Override
  protected void after() {
    destroyClient();
  }

  public void createClient() {
    client =
        ZeebeClient.newClientBuilder()
            .defaultRequestTimeout(DEFAULT_REQUEST_TIMEOUT)
            .withProperties(properties.get())
            .build();
  }

  public void destroyClient() {
    client.close();
    client = null;
  }
}
