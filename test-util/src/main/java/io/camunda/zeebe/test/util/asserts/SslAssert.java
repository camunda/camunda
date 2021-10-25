/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.test.util.netty.NettySslClient;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import org.agrona.collections.MutableReference;
import org.assertj.core.api.AbstractObjectAssert;

/**
 * An assertion class to assert SSL/TLS properties of a given {@link SocketAddress}. It uses {@link
 * NettySslClient} under the hood to connect to the remote address.
 */
public final class SslAssert extends AbstractObjectAssert<SslAssert, SocketAddress> {
  public SslAssert(final SocketAddress address) {
    super(address, SslAssert.class);
  }

  public static SslAssert assertThat(final SocketAddress address) {
    return new SslAssert(address);
  }

  /**
   * Asserts that this address ({@link #actual}) is secured via SSL or TLS using the given
   * self-signed certificate.
   *
   * <p>This assertion will fail if the address:
   *
   * <ul>
   *   <li>is not reachable
   *   <li>is not secured by SSL/TLS, e.g. is using plaintext TCP
   *   <li>is not secured with the given certificate
   * </ul>
   *
   * Example usage:
   *
   * <pre>{@code
   * final SelfSignedCertificate certificate = new SelfSignedCertificate();
   * final SocketAddress address = new InetSocketAddress("localhost", 8080);
   * SslAssert.assertThat(address).isSecuredBy(certificate);
   * }</pre>
   *
   * @param certificate the self signed certificate that should be used to secure the endpoint
   * @return this assertion for chaining
   * @throws AssertionError when {@link #actual} is not reachable
   * @throws AssertionError when {@link #actual} is null
   * @throws AssertionError when {@code certificate} is null
   * @throws AssertionError when {@link #actual} is using plaintext
   * @throws AssertionError when {@link #actual} is secured, but not with the given {@code
   *     certificate}
   * @see NettySslClient#getRemoteCertificateChain(SocketAddress)
   */
  public SslAssert isSecuredBy(final SelfSignedCertificate certificate) {
    objects.assertNotNull(info, actual, "address");
    objects.assertNotNull(info, certificate, "self signed certificate");

    final var client = NettySslClient.ofSelfSigned(certificate);
    final MutableReference<Certificate[]> certificateChain = new MutableReference<>();

    // piggy back off assertThatCode in order to fail and keep the resulting exception as the cause
    // of the generated AssertionError; using any of the protected methods, such as failWithMessage
    // would cause us to lose this information
    assertThatCode(() -> certificateChain.set(client.getRemoteCertificateChain(actual)))
        .describedAs("Failed to extract certificate from address %s", actual)
        .doesNotThrowAnyException();

    if (certificateChain.get().length == 0) {
      throw failure("No certificate reported by remote peer %s", actual);
    }

    objects.assertEqual(info, certificateChain.get()[0], certificate.cert());
    return myself;
  }
}
