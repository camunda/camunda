/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static io.camunda.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.spring.client.config.legacy.ZeebeClientStarterAutoConfigurationTest;
import io.camunda.spring.client.configuration.CamundaAutoConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = {
      CamundaAutoConfiguration.class,
      ZeebeClientStarterAutoConfigurationTest.TestConfig.class
    })
public class CamundaClientConfigurationDefaultPropertiesTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void testDefaultClientConfiguration() throws URISyntaxException {
    final CamundaClient client = applicationContext.getBean(CamundaClient.class);

    assertThat(client.getConfiguration().isPlaintextConnectionEnabled()).isFalse();
    assertThat(client.getConfiguration().getCaCertificatePath()).isNull();
    assertThat(client.getConfiguration().getCredentialsProvider())
        .isInstanceOf(NoopCredentialsProvider.class);
    assertThat(client.getConfiguration().getDefaultJobPollInterval())
        .isEqualTo(Duration.ofMillis(100));
    assertThat(client.getConfiguration().getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(client.getConfiguration().getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
    assertThat(client.getConfiguration().getDefaultJobWorkerName()).isEqualTo("default");
    assertThat(client.getConfiguration().getDefaultJobWorkerStreamEnabled()).isFalse();
    assertThat(client.getConfiguration().getDefaultJobWorkerTenantIds())
        .isEqualTo(Collections.singletonList("<default>"));
    assertThat(client.getConfiguration().getDefaultMessageTimeToLive())
        .isEqualTo(Duration.ofHours(1));
    assertThat(client.getConfiguration().getDefaultRequestTimeout())
        .isEqualTo(Duration.ofSeconds(10));
    assertThat(client.getConfiguration().getDefaultTenantId()).isEqualTo("<default>");
    assertThat(client.getConfiguration().getGatewayAddress()).isEqualTo("0.0.0.0:26500");
    assertThat(client.getConfiguration().getGrpcAddress())
        .isEqualTo(new URI("https://0.0.0.0:26500"));
    assertThat(client.getConfiguration().getKeepAlive()).isEqualTo(Duration.ofSeconds(45));
    assertThat(client.getConfiguration().getMaxMessageSize()).isEqualTo(4 * ONE_MB);
    assertThat(client.getConfiguration().getMaxMetadataSize()).isEqualTo(16 * ONE_KB);
    assertThat(client.getConfiguration().getNumJobWorkerExecutionThreads()).isEqualTo(1);
    assertThat(client.getConfiguration().getOverrideAuthority()).isNull();
    assertThat(client.getConfiguration().getRestAddress())
        .isEqualTo(new URI("https://0.0.0.0:8080"));
    assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
  }
}
