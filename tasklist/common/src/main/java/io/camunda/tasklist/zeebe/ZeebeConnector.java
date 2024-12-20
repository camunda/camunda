/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.property.ZeebeProperties;
import io.camunda.tasklist.util.ConditionalOnTasklistCompatibility;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnTasklistCompatibility(enabled = "true")
public class ZeebeConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeConnector.class);

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired private TasklistProperties tasklistProperties;

  @Bean // will be closed automatically
  public CamundaClient tasklistCamundaClient() {
    return newCamundaClient(tasklistProperties.getZeebe());
  }

  public CamundaClient newCamundaClient(final ZeebeProperties zeebeProperties) {
    LOGGER.info(
        "Zeebe Client - Using REST Configuration: {}",
        getURIFromSaaSOrProperties(zeebeProperties.getRestAddress()));
    LOGGER.info(
        "Zeebe Client - Using Gateway Configuration: {}", zeebeProperties.getGatewayAddress());
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .gatewayAddress(zeebeProperties.getGatewayAddress())
            // .restAddress(getURIFromString(zeebeProperties.getRestAddress()))
            .restAddress(getURIFromSaaSOrProperties(zeebeProperties.getRestAddress()))
            .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE);
    if (zeebeProperties.isSecure()) {
      builder.caCertificatePath(zeebeProperties.getCertificatePath());
      LOGGER.info("Use TLS connection to zeebe");
    } else {
      builder.usePlaintext();
      LOGGER.info("Use plaintext connection to zeebe");
    }

    return builder.build();
  }

  private static URI getURIFromString(final String uri) {
    try {
      return new URI(uri);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Failed to parse URI string", e);
    }
  }

  private URI getURIFromSaaSOrProperties(final String uri) {
    try {
      if (tasklistProperties.getClient().getClusterId() != null) {
        return new URI(
            "http://zeebe-service:8080/" + tasklistProperties.getClient().getClusterId());
      } else {
        return getURIFromString(uri);
      }
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Failed to parse URI string", e);
    }
  }
}
