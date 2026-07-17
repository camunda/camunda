/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.ping;

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
import io.camunda.application.commons.hub.ping.PingHubRunner.HubPingConfiguration;
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

public class PingHubRunnerIT {

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
    stubFor(
        post(urlEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\":\"test-m2m-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
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
    final String baseUrl = "http://localhost:" + wireMockServer.port();
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

    final M2MCredentials credentials =
        new M2MCredentials(URI.create(baseUrl + "/token"), "test-client-id", "test-client-secret");
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create(baseUrl + "/ping"),
            "test-cluster-name",
            pingPeriod,
            retryConfig,
            null,
            credentials);

    final PingHubRunner pingHubRunner =
        new PingHubRunner(config, managementServices, applicationContext, BROKER_TOPOLOGY_MANAGER);

    // when
    pingHubRunner.run(null);

    // then
    await().atMost(10, TimeUnit.SECONDS).until(() -> pingEventsCount() >= 3);

    // validate the first ping request received by the mock server
    final List<ServeEvent> pingEvents = pingServeEvents();
    assertThat(pingEvents).isNotEmpty();

    final String actualBody = pingEvents.get(0).getRequest().getBodyAsString();
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode expected = mapper.readTree(expectedBody);
    final JsonNode actual = mapper.readTree(actualBody);
    assertThat(actual).as("JSON payload did not match expected structure").isEqualTo(expected);
  }

  @Test
  void shouldSendM2MTokenInAuthorizationHeader() throws Exception {
    // given
    final String baseUrl = "http://localhost:" + wireMockServer.port();
    final Duration pingPeriod = Duration.ofMillis(200);

    applicationContext = mock(ApplicationContext.class);
    final Environment environment = mock(Environment.class);
    managementServices = mock(ManagementServices.class);
    when(managementServices.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(managementServices.isCommercialCamundaLicense()).thenReturn(true);
    when(managementServices.isCamundaLicenseValid()).thenReturn(true);
    when(applicationContext.getEnvironment()).thenReturn(environment);
    when(environment.getActiveProfiles()).thenReturn(new String[] {"broker"});

    final M2MCredentials credentials =
        new M2MCredentials(URI.create(baseUrl + "/token"), "test-client-id", "test-client-secret");
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create(baseUrl + "/ping"),
            "test-cluster-name",
            pingPeriod,
            new RetryConfiguration(),
            null,
            credentials);

    final PingHubRunner pingHubRunner =
        new PingHubRunner(config, managementServices, applicationContext, BROKER_TOPOLOGY_MANAGER);

    // when
    pingHubRunner.run(null);

    // then
    await().atMost(10, TimeUnit.SECONDS).until(() -> pingEventsCount() >= 1);

    final List<ServeEvent> pingEvents = pingServeEvents();
    assertThat(pingEvents).isNotEmpty();
    assertThat(pingEvents.get(0).getRequest().getHeader("Authorization"))
        .isEqualTo("Bearer test-m2m-token");
  }

  private long pingEventsCount() {
    return wireMockServer.getAllServeEvents().stream()
        .filter(e -> e.getRequest().getUrl().equals("/ping"))
        .count();
  }

  private List<ServeEvent> pingServeEvents() {
    return wireMockServer.getAllServeEvents().stream()
        .filter(e -> e.getRequest().getUrl().equals("/ping"))
        .toList();
  }
}
