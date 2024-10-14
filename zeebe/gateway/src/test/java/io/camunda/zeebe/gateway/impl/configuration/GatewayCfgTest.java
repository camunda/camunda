/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.test.util.TestConfigurationFactory;
import io.camunda.zeebe.util.Environment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class GatewayCfgTest {

  private static final String DEFAULT_CFG_FILENAME = "/configuration/gateway.default.yaml";
  private static final GatewayCfg DEFAULT_CFG = new GatewayCfg();
  private static final String EMPTY_CFG_FILENAME = "/configuration/gateway.empty.yaml";
  private static final String CUSTOM_CFG_FILENAME = "/configuration/gateway.custom.yaml";
  private static final GatewayCfg CUSTOM_CFG = new GatewayCfg();
  private static final String CUSTOM_MEMBERSHIP_CFG_FILENAME =
      "/configuration/gateway.membership.custom.yaml";

  static {
    DEFAULT_CFG.init();
    CUSTOM_CFG.init();
    CUSTOM_CFG.getNetwork().setHost("192.168.0.1").setPort(123);
    CUSTOM_CFG
        .getCluster()
        .setInitialContactPoints(List.of("foobar:1234", "barfoo:5678"))
        .setRequestTimeout(Duration.ofHours(123))
        .setClusterName("testCluster")
        .setMemberId("testMember")
        .setHost("1.2.3.4")
        .setPort(12321)
        .setConfigManager(
            new ConfigManagerCfg(
                new ClusterConfigurationGossiperConfig(
                    false, Duration.ofSeconds(5), Duration.ofSeconds(30), 6)));
    CUSTOM_CFG
        .getSecurity()
        .setEnabled(true)
        .setCertificateChainPath(new File("certificateChainPath"))
        .setPrivateKeyPath(new File("privateKeyPath"));
    CUSTOM_CFG.getThreads().setManagementThreads(100);
    CUSTOM_CFG.getLongPolling().setEnabled(false);
    CUSTOM_CFG.getMultiTenancy().setEnabled(true);
    CUSTOM_CFG.getInterceptors().add(new InterceptorCfg());
    CUSTOM_CFG.getInterceptors().get(0).setId("example");
    CUSTOM_CFG.getInterceptors().get(0).setClassName("io.camunda.zeebe.example.Interceptor");
    CUSTOM_CFG.getInterceptors().get(0).setJarPath("./interceptor.jar");
    CUSTOM_CFG.getInterceptors().add(new InterceptorCfg());
    CUSTOM_CFG.getInterceptors().get(1).setId("example2");
    CUSTOM_CFG.getInterceptors().get(1).setClassName("io.camunda.zeebe.example.Interceptor2");
    CUSTOM_CFG.getInterceptors().get(1).setJarPath("./interceptor2.jar");
    CUSTOM_CFG.getFilters().add(new FilterCfg());
    CUSTOM_CFG.getFilters().get(0).setId("filterExample");
    CUSTOM_CFG.getFilters().get(0).setClassName("io.camunda.zeebe.example.Filter");
    CUSTOM_CFG.getFilters().get(0).setJarPath("./filter.jar");
    CUSTOM_CFG.getFilters().add(new FilterCfg());
    CUSTOM_CFG.getFilters().get(1).setId("filterExample2");
    CUSTOM_CFG.getFilters().get(1).setClassName("io.camunda.zeebe.example.Filter2");
    CUSTOM_CFG.getFilters().get(1).setJarPath("./filter2.jar");
  }

  private final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldHaveDefaultValues() {
    // when
    final GatewayCfg gatewayCfg = readDefaultConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(DEFAULT_CFG);
  }

  @Test
  public void shouldLoadEmptyConfig() {
    // when
    final GatewayCfg gatewayCfg = readEmptyConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(DEFAULT_CFG);
  }

  @Test
  public void shouldLoadCustomConfig() {
    // when
    final GatewayCfg gatewayCfg = readCustomConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(CUSTOM_CFG);
  }

  @Test
  public void shouldSetCustomMembershipConfig() {
    // when
    final GatewayCfg gatewayCfg = readConfig(CUSTOM_MEMBERSHIP_CFG_FILENAME);

    // then
    final var membershipCfg = gatewayCfg.getCluster().getMembership();

    assertThat(membershipCfg.isBroadcastDisputes()).isFalse();
    assertThat(membershipCfg.isBroadcastUpdates()).isTrue();
    assertThat(membershipCfg.isNotifySuspect()).isTrue();
    assertThat(membershipCfg.getGossipInterval()).isEqualTo(Duration.ofSeconds(2));
    assertThat(membershipCfg.getGossipFanout()).isEqualTo(3);
    assertThat(membershipCfg.getProbeInterval()).isEqualTo(Duration.ofSeconds(3));
    assertThat(membershipCfg.getProbeTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(membershipCfg.getSuspectProbes()).isEqualTo(5);
    assertThat(membershipCfg.getFailureTimeout()).isEqualTo(Duration.ofSeconds(20));
    assertThat(membershipCfg.getSyncInterval()).isEqualTo(Duration.ofSeconds(25));
  }

  @Test
  public void shouldSetCustomConfigManagerCfg() {
    // when
    final GatewayCfg gatewayCfg = readConfig(CUSTOM_CFG_FILENAME);

    // then
    final var gossiperConfig = gatewayCfg.getCluster().getConfigManager().gossip();
    assertThat(gossiperConfig.enableSync()).isEqualTo(false);
    assertThat(gossiperConfig.syncDelay()).isEqualTo(Duration.ofSeconds(5));
    assertThat(gossiperConfig.syncRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(gossiperConfig.gossipFanout()).isEqualTo(6);
  }

  @Test
  public void shouldUseEnvironmentVariables() {
    // given
    setEnv("zeebe.gateway.network.host", "zeebe");
    setEnv("zeebe.gateway.network.port", "5432");
    setEnv("zeebe.gateway.cluster.initialContactPoints", "broker:432,anotherBroker:789");
    setEnv("zeebe.gateway.threads.managementThreads", "32");
    setEnv("zeebe.gateway.cluster.requestTimeout", Duration.ofMinutes(43).toString());
    setEnv("zeebe.gateway.cluster.longPollingEnabled", "false");
    setEnv("zeebe.gateway.cluster.clusterName", "envCluster");
    setEnv("zeebe.gateway.cluster.memberId", "envMember");
    setEnv("zeebe.gateway.cluster.host", "envHost");
    setEnv("zeebe.gateway.cluster.port", "12345");
    setEnv("zeebe.gateway.cluster.configManager.gossip.enableSync", "false");
    setEnv("zeebe.gateway.cluster.configManager.gossip.syncDelay", "5s");
    setEnv("zeebe.gateway.cluster.configManager.gossip.syncRequestTimeout", "5s");
    setEnv("zeebe.gateway.cluster.configManager.gossip.gossipFanout", "4");
    setEnv("zeebe.gateway.security.enabled", String.valueOf(false));
    setEnv(
        "zeebe.gateway.security.privateKeyPath",
        GatewayCfgTest.class
            .getClassLoader()
            .getResource("security/test-server.key.pem")
            .getPath());
    setEnv(
        "zeebe.gateway.security.certificateChainPath",
        GatewayCfgTest.class
            .getClassLoader()
            .getResource("security/test-chain.cert.pem")
            .getPath());
    setEnv("zeebe.gateway.network.minKeepAliveInterval", Duration.ofSeconds(30).toString());
    setEnv("zeebe.gateway.longPolling.enabled", String.valueOf(true));
    setEnv("zeebe.gateway.multiTenancy.enabled", String.valueOf(false));
    setEnv("zeebe.gateway.interceptors.0.id", "overwritten");
    setEnv("zeebe.gateway.interceptors.0.className", "Overwritten");
    setEnv("zeebe.gateway.interceptors.0.jarPath", "./overwritten.jar");
    setEnv("zeebe.gateway.filters.0.id", "overwrittenFilter");
    setEnv("zeebe.gateway.filters.0.className", "OverwrittenFilter");
    setEnv("zeebe.gateway.filters.0.jarPath", "./overwrittenFilter.jar");

    final GatewayCfg expected = new GatewayCfg();
    expected
        .getNetwork()
        .setHost("zeebe")
        .setPort(5432)
        .setMinKeepAliveInterval(Duration.ofSeconds(30));
    expected
        .getCluster()
        .setInitialContactPoints(List.of("broker:432", "anotherBroker:789"))
        .setRequestTimeout(Duration.ofMinutes(43))
        .setClusterName("envCluster")
        .setMemberId("envMember")
        .setHost("envHost")
        .setPort(12345);
    expected
        .getCluster()
        .setConfigManager(
            new ConfigManagerCfg(
                new ClusterConfigurationGossiperConfig(
                    false, Duration.ofSeconds(5), Duration.ofSeconds(5), 4)));
    expected.getThreads().setManagementThreads(32);
    expected
        .getSecurity()
        .setEnabled(false)
        .setPrivateKeyPath(
            new File(
                getClass().getClassLoader().getResource("security/test-server.key.pem").getPath()))
        .setCertificateChainPath(
            new File(
                getClass().getClassLoader().getResource("security/test-chain.cert.pem").getPath()));
    expected.getLongPolling().setEnabled(true);
    expected.getMultiTenancy().setEnabled(false);

    expected.getInterceptors().add(new InterceptorCfg());
    expected.getInterceptors().get(0).setId("overwritten");
    expected.getInterceptors().get(0).setClassName("Overwritten");
    expected.getInterceptors().get(0).setJarPath("./overwritten.jar");

    expected.getFilters().add(new FilterCfg());
    expected.getFilters().get(0).setId("overwrittenFilter");
    expected.getFilters().get(0).setClassName("OverwrittenFilter");
    expected.getFilters().get(0).setJarPath("./overwrittenFilter.jar");

    // when
    final GatewayCfg gatewayCfg = readCustomConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(expected);
  }

  @Test
  public void shouldSetInitialContactPointsWhenSetContactPoint() {
    // given
    final String contactPoint = "foo-bar:1";

    // when
    final GatewayCfg gatewayCfg =
        new GatewayCfg().setCluster(new ClusterCfg().setContactPoint(contactPoint));

    // then
    assertThat(gatewayCfg.getCluster().getInitialContactPoints()).containsExactly(contactPoint);
  }

  @Test
  public void shouldSetInitialContactPointsWhenUseContactPointEnvironmentVariable() {
    // given
    final String contactPoint = "broker:789";
    setEnv("zeebe.gateway.cluster.contactPoint", contactPoint);

    final GatewayCfg expected =
        new GatewayCfg()
            .setCluster(new ClusterCfg().setInitialContactPoints(List.of(contactPoint)));
    expected.init();

    // when
    final GatewayCfg gatewayCfg = readDefaultConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(expected);
  }

  @Test
  public void shouldSetInitialContactPointsWhenUseContactPointConfig() {
    // given
    final String contactPoint = "broker:789";
    final GatewayCfg expected =
        new GatewayCfg()
            .setCluster(new ClusterCfg().setInitialContactPoints(List.of(contactPoint)));
    expected.init();

    // when
    final GatewayCfg gatewayCfg =
        readConfig("/configuration/gateway.deprecated.contactPoint.custom.yaml");

    // then
    assertThat(gatewayCfg).isEqualTo(expected);
  }

  @Test
  public void shouldFallbackIfAdvertisedAddressIsNotConfigured() {
    // given
    final var expectedHost = "zeebe";
    final var expectedPort = "5432";
    setEnv("zeebe.gateway.cluster.host", expectedHost);
    setEnv("zeebe.gateway.cluster.port", expectedPort);

    // when
    final GatewayCfg actual = readEmptyConfig();

    // then
    assertThat(actual.getCluster().getAdvertisedHost()).isEqualTo(expectedHost);
    assertThat(actual.getCluster().getAdvertisedPort()).isEqualTo(Integer.parseInt(expectedPort));
  }

  @Test
  public void shouldUseFirstNonLoopBackAdvertisedAddressIfNothingSet() {
    // given
    final var expectedHost = Address.defaultAdvertisedHost().getHostAddress();
    final var expectedPort = "5432";
    setEnv("zeebe.gateway.cluster.host", null);
    setEnv("zeebe.gateway.cluster.port", expectedPort);

    // when
    final GatewayCfg actual = readEmptyConfig();

    // then
    assertThat(actual.getCluster().getAdvertisedHost()).isEqualTo(expectedHost);
    assertThat(actual.getCluster().getAdvertisedPort()).isEqualTo(Integer.parseInt(expectedPort));
  }

  private void setEnv(final String key, final String value) {
    environment.put(key, value);
  }

  private GatewayCfg readDefaultConfig() {
    return readConfig(DEFAULT_CFG_FILENAME);
  }

  private GatewayCfg readEmptyConfig() {
    return readConfig(EMPTY_CFG_FILENAME);
  }

  private GatewayCfg readCustomConfig() {
    return readConfig(CUSTOM_CFG_FILENAME);
  }

  private GatewayCfg readConfig(final String filename) {
    try (final InputStream inputStream = GatewayCfgTest.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        final GatewayCfg gatewayCfg =
            new TestConfigurationFactory()
                .create(new Environment(environment), "zeebe.gateway", filename, GatewayCfg.class);
        gatewayCfg.init();
        return gatewayCfg;
      } else {
        throw new AssertionError("Unable to find configuration file: " + filename);
      }
    } catch (final IOException e) {
      throw new AssertionError("Failed to read configuration from file: " + filename, e);
    }
  }
}
