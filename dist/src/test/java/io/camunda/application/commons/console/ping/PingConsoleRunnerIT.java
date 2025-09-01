/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.LicenseType;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

public class PingConsoleRunnerIT {

  private static final BrokerTopologyManager BROKER_TOPOLOGY_MANAGER =
      mock(BrokerTopologyManager.class);
  private static final ClusterConfiguration BROKER_CLUSTER_CONFIGURATION =
      mock(ClusterConfiguration.class);
  private ApplicationContext applicationContext;
  private WireMockServer wireMockServer;
  private ManagementServices managementServices;

  @BeforeEach
  void setup() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
    configureFor("localhost", wireMockServer.port());
    stubFor(post(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("PONG")));
    when(BROKER_TOPOLOGY_MANAGER.getClusterConfiguration())
        .thenReturn(BROKER_CLUSTER_CONFIGURATION);
    when(BROKER_CLUSTER_CONFIGURATION.clusterId()).thenReturn(Optional.of("test-cluster-id"));
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void shouldSendCorrectPayload() throws Exception {
    // given
    final String mockUrl = "http://localhost:" + wireMockServer.port() + "/ping";
    final Duration pingPeriod = Duration.ofMillis(200);
    final RetryConfiguration retryConfig = new RetryConfiguration();
    final String expectedBody =
        """
      {
        "license": {
          "validLicense": true,
          "licenseType": "saas",
          "isCommercial": true
        },
        "clusterId": "test-cluster-id",
        "clusterName": "test-cluster-name",
        "version":"%s",
        "profiles": ["gateway", "broker"]
      }
    """
            .formatted(VersionUtil.getVersion());

    applicationContext = mock(ApplicationContext.class);
    final Environment environment = mock(Environment.class);
    managementServices = mock(ManagementServices.class);
    when(managementServices.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(managementServices.isCommercialCamundaLicense()).thenReturn(true);
    when(managementServices.isCamundaLicenseValid()).thenReturn(true);
    when(applicationContext.getEnvironment()).thenReturn(environment);
    when(environment.getActiveProfiles())
        .thenReturn(new String[] {"gateway", "broker", "identity"});

    final ConsolePingConfiguration config =
        new ConsolePingConfiguration(
            true, URI.create(mockUrl), "test-cluster-name", pingPeriod, retryConfig, null);

    final PingConsoleRunner pingConsoleRunner =
        new PingConsoleRunner(
            config, managementServices, applicationContext, BROKER_TOPOLOGY_MANAGER);

    // when
    pingConsoleRunner.run(null);

    // then
    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> wireMockServer.getAllServeEvents().size() >= 3);

    // we validate the first request received by the mock server
    final List<ServeEvent> serveEvents = wireMockServer.getAllServeEvents();
    final String actualBody = serveEvents.get(0).getRequest().getBodyAsString();

    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode expected = mapper.readTree(expectedBody);
    final JsonNode actual = mapper.readTree(actualBody);

    assertThat(actual).as("JSON payload did not match expected structure").isEqualTo(expected);
  }
}
