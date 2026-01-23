/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.property.ZeebeProperties;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TasklistZeebeExtension;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.zeebe.ZeebeConnector;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.time.Duration;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@AutoConfigureTestRestTemplate
@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      TasklistProperties.PREFIX + ".zeebe.gatewayAddress = localhost:55500",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZeebeConnectorIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired DatabaseTestExtension databaseTestExtension;

  @Autowired private TasklistZeebeExtension zeebeExtension;

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private ZeebeConnector zeebeConnector;

  private CamundaClient camundaClient;

  @LocalManagementPort private int managementPort;

  @AfterEach
  public void cleanup() {
    if (camundaClient != null) {
      camundaClient.close();
    }
    zeebeExtension.afterEach(null);
  }

  @Test
  public void testZeebeConnection() throws Exception {
    // when 1
    // no Zeebe broker is running

    // then 1
    // application context must be successfully started
    testRequest("http://localhost:" + managementPort + "/actuator/health/liveness");

    // when 2
    // Zeebe is started
    startZeebe();

    camundaClient =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(false)
            .grpcAddress(zeebeExtension.getZeebeBroker().grpcAddress())
            .restAddress(zeebeExtension.getZeebeBroker().restAddress())
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();

    // then 2
    assertThat(camundaClient.newTopologyRequest().send().join().getBrokers()).isNotEmpty();
  }

  private void testRequest(final String url) {
    final ResponseEntity<Object> entity =
        testRestTemplate.exchange(url, HttpMethod.GET, null, Object.class);
    assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
  }

  private void startZeebe() {
    zeebeExtension.beforeEach(null);
  }

  @Test
  public void testRecoverAfterZeebeRestart() throws Exception {
    // when 1
    // Zeebe is started
    startZeebe();

    camundaClient =
        zeebeConnector.newCamundaClient(
            new ZeebeProperties()
                .setGatewayAddress(zeebeExtension.getZeebeBroker().address(TestZeebePort.GATEWAY)));

    // then 1
    assertThat(camundaClient.newTopologyRequest().send().join().getBrokers()).isNotEmpty();

    camundaClient.close();

    // when 2
    // Zeebe is restarted
    zeebeExtension.afterEach(null);
    zeebeExtension.beforeEach(null);

    camundaClient =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(false)
            .grpcAddress(zeebeExtension.getZeebeBroker().grpcAddress())
            .restAddress(zeebeExtension.getZeebeBroker().restAddress())
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();

    // then 2
    assertThat(camundaClient.newTopologyRequest().send().join().getBrokers()).isNotEmpty();
  }
}
