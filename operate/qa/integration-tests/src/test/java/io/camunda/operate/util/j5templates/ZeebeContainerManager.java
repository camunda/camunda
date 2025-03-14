/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.Topology;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.security.configuration.SecurityConfiguration;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;

public abstract class ZeebeContainerManager {

  protected static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  protected final OperateProperties operateProperties;
  protected final TestContainerUtil testContainerUtil;
  protected String prefix;
  protected ZeebeContainer zeebeContainer;
  protected CamundaClient client;
  private final SecurityConfiguration securityConfiguration;

  public ZeebeContainerManager(
      final OperateProperties operateProperties,
      final SecurityConfiguration securityConfiguration,
      final TestContainerUtil testContainerUtil,
      final String indexPrefix) {
    this.operateProperties = operateProperties;
    this.securityConfiguration = securityConfiguration;
    this.testContainerUtil = testContainerUtil;
    prefix = indexPrefix;
  }

  public CamundaClient getClient() {
    return client;
  }

  public void startContainer() {
    updatePrefix();

    // Start zeebe
    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME);
    zeebeContainer =
        testContainerUtil.startZeebe(
            zeebeVersion,
            prefix,
            2,
            securityConfiguration.getMultiTenancy().isEnabled(),
            ConnectionTypes.ELASTICSEARCH.getType());

    client =
        CamundaClient.newClientBuilder()
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
