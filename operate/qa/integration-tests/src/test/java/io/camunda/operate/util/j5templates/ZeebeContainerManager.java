/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.TestUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;

public abstract class ZeebeContainerManager {

  protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  protected final OperateProperties operateProperties;
  protected final TestContainerUtil testContainerUtil;
  protected String prefix;
  protected ZeebeContainer zeebeContainer;
  protected ZeebeClient client;

  public ZeebeContainerManager(
      final OperateProperties operateProperties, final TestContainerUtil testContainerUtil) {
    this.operateProperties = operateProperties;
    this.testContainerUtil = testContainerUtil;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public void startContainer() {
    prefix = TestUtil.createRandomString(10);
    updatePrefix();

    // Start zeebe
    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    zeebeContainer =
        testContainerUtil.startZeebe(
            zeebeVersion, prefix, 2, operateProperties.getMultiTenancy().isEnabled());

    client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .usePlaintext()
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();

    // Test zeebe is ready
    // get topology to check that cluster is available and ready for work
    Topology topology = null;
    while (topology == null) {
      try {
        topology = client.newTopologyRequest().send().join();
      } catch (final ClientException ex) {
        ex.printStackTrace();
      } catch (final Exception e) {
        e.printStackTrace();
        break;
        // exit
      }
    }
  }

  protected abstract void updatePrefix();

  public void stopContainer() {
    testContainerUtil.stopZeebe(null);

    if (client != null) {
      client.close();
      client = null;
    }

    removeIndices();
  }

  protected abstract void removeIndices();
}
