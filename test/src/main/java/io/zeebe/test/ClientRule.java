/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import java.util.Properties;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {

  protected final Supplier<Properties> properties;
  protected ZeebeClient client;

  public ClientRule() {
    this(Properties::new);
  }

  public ClientRule(final Supplier<Properties> propertiesProvider) {
    this.properties = propertiesProvider;
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
    client = ZeebeClient.newClientBuilder().withProperties(properties.get()).build();
  }

  public void destroyClient() {
    client.close();
    client = null;
  }
}
