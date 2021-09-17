/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.security;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.util.sched.ActorScheduler;
import java.io.IOException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class SecurityTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();
  private Gateway gateway;

  @After
  public void tearDown() {
    if (gateway != null) {
      gateway.stop();
      gateway = null;
    }
  }

  @Test
  public void shouldNotStartWithTlsEnabledAndWrongCert() throws IOException {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity()
        .setCertificateChainPath(
            cfg.getSecurity().getCertificateChainPath().replaceAll("test-chain", "wrong-chain"));

    // then
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        String.format(
            "Expected to find a certificate chain file at the provided location '%s' but none was found.",
            cfg.getSecurity().getCertificateChainPath()));

    // when
    gateway = buildGateway(cfg);
    gateway.start();
  }

  @Test
  public void shouldNotStartWithTlsEnabledAndWrongKey() throws IOException {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity()
        .setPrivateKeyPath(
            cfg.getSecurity().getPrivateKeyPath().replaceAll("test-server", "wrong-server"));

    // then
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        String.format(
            "Expected to find a private key file at the provided location '%s' but none was found.",
            cfg.getSecurity().getPrivateKeyPath()));

    // when
    gateway = buildGateway(cfg);
    gateway.start();
  }

  @Test
  public void shouldNotStartWithTlsEnabledAndNoPrivateKey() throws IOException {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity().setPrivateKeyPath(null);

    // then
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Expected to find a valid path to a private key but none was found. "
            + "Edit the gateway configuration file to provide one or to disable TLS.");

    // when
    gateway = buildGateway(cfg);
    gateway.start();
  }

  @Test
  public void shouldNotStartWithTlsEnabledAndNoCert() throws IOException {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity().setCertificateChainPath(null);

    // then
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Expected to find a valid path to a certificate chain but none was found. "
            + "Edit the gateway configuration file to provide one or to disable TLS.");

    // when
    gateway = buildGateway(cfg);
    gateway.start();
  }

  private GatewayCfg createGatewayCfg() {
    return new GatewayCfg()
        .setNetwork(new NetworkCfg().setHost("localhost").setPort(25600))
        .setSecurity(
            new SecurityCfg()
                .setEnabled(true)
                .setCertificateChainPath(
                    getClass()
                        .getClassLoader()
                        .getResource("security/test-chain.cert.pem")
                        .getPath())
                .setPrivateKeyPath(
                    getClass()
                        .getClassLoader()
                        .getResource("security/test-server.key.pem")
                        .getPath()));
  }

  private Gateway buildGateway(final GatewayCfg gatewayCfg) {
    final var atomix = AtomixCluster.builder().build();
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    return new Gateway(
        gatewayCfg,
        atomix.getMessagingService(),
        atomix.getMembershipService(),
        atomix.getEventService(),
        actorScheduler);
  }
}
