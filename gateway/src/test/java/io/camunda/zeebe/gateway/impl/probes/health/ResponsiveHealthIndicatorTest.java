/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.probes.health.HealthZeebeClientProperties.SecurityProperties;
import io.camunda.zeebe.gateway.impl.probes.health.HealthZeebeClientProperties.SecurityProperties.OAuthSecurityProperties;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.springframework.boot.actuate.health.Status;

public class ResponsiveHealthIndicatorTest {
  private static final Duration TEST_DURATION = Duration.ofSeconds(123);
  private static final HealthZeebeClientProperties ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION =
      new HealthZeebeClientProperties();
  private static final GatewayCfg TEST_CFG = new GatewayCfg();
  private static final String CERTIFICATE_CHAIN_PATH =
      ResponsiveHealthIndicatorTest.class
          .getClassLoader()
          .getResource("security/test-chain.cert.pem")
          .getPath();

  static {
    TEST_CFG.getNetwork().setHost("testhost");
    TEST_CFG.getNetwork().setPort(1234);
    TEST_CFG.getSecurity().setEnabled(false);
    TEST_CFG.init();

    ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION.setRequestTimeout(TEST_DURATION);
  }

  @Test
  public void shouldRejectNullConfigInConstructor() {
    assertThatThrownBy(
            () -> new ResponsiveHealthIndicator(null, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION))
        .isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldRejectNullDurationInConstructor() {
    assertThatThrownBy(
            () -> new ResponsiveHealthIndicator(TEST_CFG, (HealthZeebeClientProperties) null))
        .isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportUnknownIfNoZeebeClientCanBeSupplied() {
    // given
    final var healthIndicator =
        new ResponsiveHealthIndicator(TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);

    final var spyHealthIndicator = spy(healthIndicator);
    doNothing().when(spyHealthIndicator).initZeebeClient();

    // when
    final var actualHealth = spyHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isSameAs(Status.UNKNOWN);
  }

  @Test
  public void shouldReportDownStatusIfRequestThrowsException()
      throws ExecutionException, InterruptedException {
    // given
    final var mockZeebeClient = mock(ZeebeClient.class);
    final var mockTopologyRequestStep1 = mock(TopologyRequestStep1.class);
    final var mockZeebeFuture = mock(ZeebeFuture.class);

    // build mock chain that throws an exception on get() method call
    when(mockZeebeClient.newTopologyRequest()).thenReturn(mockTopologyRequestStep1);
    when(mockTopologyRequestStep1.send()).thenReturn(mockZeebeFuture);
    when(mockZeebeFuture.get()).thenThrow(new InterruptedException());

    final var healthIndicator =
        new ResponsiveHealthIndicator(TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);
    final var spyHealthIndicator = spy(healthIndicator);
    when(spyHealthIndicator.getZeebeClient()).thenReturn(mockZeebeClient);

    // when
    final var actualHealth = spyHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isSameAs(Status.DOWN);
  }

  @Test
  public void shouldReportUpStatusIfRequestSucceeds()
      throws ExecutionException, InterruptedException {
    // given
    final var mockZeebeClient = mock(ZeebeClient.class);
    final var mockTopologyRequestStep1 = mock(TopologyRequestStep1.class);
    final var mockZeebeFuture = mock(ZeebeFuture.class);

    // build mock chain
    when(mockZeebeClient.newTopologyRequest()).thenReturn(mockTopologyRequestStep1);
    when(mockTopologyRequestStep1.send()).thenReturn(mockZeebeFuture);

    final var healthIndicator =
        new ResponsiveHealthIndicator(TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);
    final var spyHealthIndicator = spy(healthIndicator);
    doAnswer(
            invocation -> {
              spyHealthIndicator.setZeebeClient(mockZeebeClient);
              return null;
            })
        .when(spyHealthIndicator)
        .initZeebeClient();

    // when
    final var actualHealth = spyHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isSameAs(Status.UP);
  }

  @Test
  public void shouldNotCloseZeebeClientInHealthChecks() {
    // given
    final var mockZeebeClient = mock(ZeebeClient.class);
    final var mockTopologyRequestStep1 = mock(TopologyRequestStep1.class);
    final var mockZeebeFuture = mock(ZeebeFuture.class);

    // build mock chain
    when(mockZeebeClient.newTopologyRequest()).thenReturn(mockTopologyRequestStep1);
    when(mockTopologyRequestStep1.send()).thenReturn(mockZeebeFuture);

    final var healthIndicator =
        new ResponsiveHealthIndicator(TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);
    final var spyHealthIndicator = spy(healthIndicator);
    doAnswer(
            invocation -> {
              spyHealthIndicator.setZeebeClient(mockZeebeClient);
              return null;
            })
        .when(spyHealthIndicator)
        .initZeebeClient();

    // when
    spyHealthIndicator.health();

    // then
    verify(mockZeebeClient, never()).close();
  }

  @Test
  public void shouldNotSupplyZeebeClientIfGatewayConfigIsNotInitialized() {
    // given
    final var gatewayCfg = new GatewayCfg();
    gatewayCfg.getNetwork().setHost("testhost");
    gatewayCfg.getNetwork().setPort(1234);
    gatewayCfg.getSecurity().setEnabled(false);

    final var healthIndicator =
        new ResponsiveHealthIndicator(gatewayCfg, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);

    // when
    final var actualZeebeClient = healthIndicator.getZeebeClient();

    // then
    assertThat(actualZeebeClient).isNull();
  }

  @Test
  public void shouldSupplyConfiguredZeebeClientIfGatewayConfigIsInitialized() {
    // given
    final var gatewayCfg = new GatewayCfg();
    gatewayCfg.getNetwork().setHost("testhost");
    gatewayCfg.getNetwork().setPort(1234);
    gatewayCfg.getSecurity().setEnabled(false);
    gatewayCfg.init();

    final var healthIndicator =
        new ResponsiveHealthIndicator(gatewayCfg, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);
    healthIndicator.initZeebeClient();

    // when
    final var actualZeebeClient = healthIndicator.getZeebeClient();

    // then
    assertThat(actualZeebeClient).isNotNull();
    assertThat(actualZeebeClient.getConfiguration().getGatewayAddress()).isEqualTo("testhost:1234");
    assertThat(actualZeebeClient.getConfiguration().isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  public void testGetContactPoint() {
    // when
    final String actualContactPoint = ResponsiveHealthIndicator.getContactPoint(TEST_CFG);

    // then
    assertThat(actualContactPoint).isEqualTo("testhost:1234");
  }

  @Test
  public void testCreateZeebeClientShouldConfigureContactPoint() {
    // when
    final ZeebeClient actual =
        ResponsiveHealthIndicator.createZeebeClient(
            TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);

    // then
    assertThat(actual.getConfiguration().getGatewayAddress()).isEqualTo("testhost:1234");
  }

  @Test
  public void testCreateZeebeClientShouldConfigureRequestTimeout() {
    // when
    final ZeebeClient actual =
        ResponsiveHealthIndicator.createZeebeClient(
            TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);

    // then
    assertThat(actual.getConfiguration().getDefaultRequestTimeout()).isEqualTo(TEST_DURATION);
  }

  @Test
  public void testCreateZeebeClientShouldEnablePlainTextCommunicationIfSecurityIsDisabled() {
    // when
    final ZeebeClient actual =
        ResponsiveHealthIndicator.createZeebeClient(
            TEST_CFG, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);

    // then
    assertThat(actual.getConfiguration().isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  public void testCreateZeebeClientShouldPassCertificateChainPathIfSecurityIsEnabled() {
    // given
    final var securityEnabledGatewayCfg = new GatewayCfg();
    securityEnabledGatewayCfg.getNetwork().setHost("testhost");
    securityEnabledGatewayCfg.getNetwork().setPort(1234);
    securityEnabledGatewayCfg.getSecurity().setEnabled(true);
    securityEnabledGatewayCfg
        .getSecurity()
        .setCertificateChainPath(new File(CERTIFICATE_CHAIN_PATH));
    securityEnabledGatewayCfg.init();

    // when
    final ZeebeClient actualZeebeClient =
        ResponsiveHealthIndicator.createZeebeClient(
            securityEnabledGatewayCfg, ZEEBE_CLIENT_PROPERTIES_WITH_TEST_DURATION);

    // then
    assertThat(actualZeebeClient.getConfiguration().isPlaintextConnectionEnabled()).isFalse();
    assertThat(actualZeebeClient.getConfiguration().getCaCertificatePath())
        .isEqualTo(CERTIFICATE_CHAIN_PATH);
  }

  @Test
  public void shouldCreateZeebeClientReturnClientWithAuthenticationSettings() throws IOException {
    // given
    final HealthZeebeClientProperties healthZeebeClientProperties =
        new HealthZeebeClientProperties();
    final SecurityProperties securityProperties = new SecurityProperties();
    final OAuthSecurityProperties oauthSecurityProperties = new OAuthSecurityProperties();
    oauthSecurityProperties.setClientId("clientId");
    oauthSecurityProperties.setAudience("audience");
    oauthSecurityProperties.setAuthorizationServer(new URL("http://localhost:1"));
    oauthSecurityProperties.setCredentialsCache(File.createTempFile("zeebe", "test"));
    oauthSecurityProperties.setReadTimeout(Duration.ofSeconds(1));
    oauthSecurityProperties.setReadTimeout(Duration.ofSeconds(2));
    oauthSecurityProperties.setClientSecret("clientSecret");
    securityProperties.setOauthSecurityProperties(oauthSecurityProperties);
    healthZeebeClientProperties.setSecurityProperties(securityProperties);

    // when
    final ZeebeClient zeebeClient =
        ResponsiveHealthIndicator.createZeebeClient(TEST_CFG, healthZeebeClientProperties);

    // then
    final CredentialsProvider credentialsProvider =
        zeebeClient.getConfiguration().getCredentialsProvider();
    assertThat(credentialsProvider).isNotNull().isExactlyInstanceOf(OAuthCredentialsProvider.class);
  }
}
