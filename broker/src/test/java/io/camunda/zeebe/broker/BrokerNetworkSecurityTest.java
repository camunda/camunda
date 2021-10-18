/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.SecurityCfg;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.SocketAddress;
import java.security.cert.CertificateException;
import org.junit.ClassRule;
import org.junit.Test;

public final class BrokerNetworkSecurityTest {
  // must be public because it must be declared before BROKER, and checkstyle flags it if we set it
  // anything else as a false positive
  public static final SelfSignedCertificate CERTIFICATE;

  @ClassRule
  public static final EmbeddedBrokerRule BROKER =
      new EmbeddedBrokerRule(BrokerNetworkSecurityTest::configureBroker);

  static {
    try {
      CERTIFICATE = new SelfSignedCertificate();
    } catch (final CertificateException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldSecureCommandApi() {
    // given
    final SocketAddress commandApiAddress =
        BROKER.getBrokerCfg().getNetwork().getCommandApi().getAddress();

    // then
    SslAssert.assertThat(commandApiAddress).isSecuredBy(CERTIFICATE);
  }

  @Test
  public void shouldSecureInternalApi() {
    // given
    final SocketAddress internalApiAddress =
        BROKER.getBrokerCfg().getNetwork().getInternalApi().getAddress();

    // then
    SslAssert.assertThat(internalApiAddress).isSecuredBy(CERTIFICATE);
  }

  private static void configureBroker(final BrokerCfg config) {
    config
        .getNetwork()
        .setSecurity(
            new SecurityCfg()
                .setEnabled(true)
                .setCertificateChainPath(CERTIFICATE.certificate().getAbsolutePath())
                .setPrivateKeyPath(CERTIFICATE.privateKey().getAbsolutePath()));
  }
}
