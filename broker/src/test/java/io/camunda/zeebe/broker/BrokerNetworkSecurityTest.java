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
import org.agrona.LangUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class BrokerNetworkSecurityTest {
  @ClassRule
  public static final SecuredEmbeddedBrokerRule BROKER_RULE = new SecuredEmbeddedBrokerRule();

  @Test
  public void shouldSecureCommandApi() {
    // given
    final SocketAddress commandApiAddress =
        BROKER_RULE.broker.getBrokerCfg().getNetwork().getCommandApi().getAddress();

    // then
    SslAssert.assertThat(commandApiAddress).isSecuredBy(BROKER_RULE.certificate);
  }

  @Test
  public void shouldSecureInternalApi() {
    // given
    final SocketAddress internalApiAddress =
        BROKER_RULE.broker.getBrokerCfg().getNetwork().getInternalApi().getAddress();

    // then
    SslAssert.assertThat(internalApiAddress).isSecuredBy(BROKER_RULE.certificate);
  }

  private static final class SecuredEmbeddedBrokerRule extends ExternalResource {
    private SelfSignedCertificate certificate;
    private EmbeddedBrokerRule broker;

    @Override
    public Statement apply(final Statement base, final Description description) {
      try {
        certificate = new SelfSignedCertificate();
      } catch (final CertificateException e) {
        LangUtil.rethrowUnchecked(e);
      }

      broker = new EmbeddedBrokerRule(this::configureBroker);
      final var statement = super.apply(base, description);
      return broker.apply(statement, description);
    }

    private void configureBroker(final BrokerCfg config) {
      config
          .getNetwork()
          .setSecurity(
              new SecurityCfg()
                  .setEnabled(true)
                  .setCertificateChainPath(certificate.certificate())
                  .setPrivateKeyPath(certificate.privateKey()));
    }
  }
}
