/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.security;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;

import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.utility.MountableFile;

/**
 * The following test uses containers, specifically because Netty's PKCS1 support relies on
 * BouncyCastle being available on the classpath. Since it was already available in this module, the
 * tests were passing without problem, but as the dependency was only test scoped, it would fail in
 * production.
 *
 * <p>So we use containers to test the actual production classpath as well.
 */
final class Pkcs1SupportTest {
  @RegressionTest("https://github.com/camunda/camunda/issues/15977")
  void shouldSupportPkcs1Key(final @TempDir Path tmpDir) throws IOException, CertificateException {
    // given
    final var certificate = new SelfSignedCertificate();
    final var pkcs1Key = convertToPkcs1(certificate.key(), tmpDir.resolve("private.key").toFile());
    final var containerCertPath = "/usr/local/zeebe/cert.crt";
    final var containerKeyPath = "/usr/local/zeebe/pkcs1.key";
    final var zeebe =
        new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withCopyFileToContainer(
                MountableFile.forHostPath(certificate.certificate().toPath(), 511),
                containerCertPath)
            .withCopyFileToContainer(
                MountableFile.forHostPath(pkcs1Key.toPath(), 511), containerKeyPath)
            .withAdditionalExposedPort(8080)
            .withEnv("SERVER_SSL_ENABLED", "true")
            .withEnv("SERVER_SSL_CERTIFICATE", containerCertPath)
            .withEnv("SERVER_SSL_CERTIFICATEPRIVATEKEY", containerKeyPath)
            .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_ENABLED", "true")
            .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_CERTIFICATECHAINPATH", containerCertPath)
            .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_PRIVATEKEYPATH", containerKeyPath)
            .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_ENABLED", "true")
            .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_CERTIFICATECHAINPATH", containerCertPath)
            .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_PRIVATEKEYPATH", containerKeyPath)
            .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
            .withoutTopologyCheck(); // avoid the missing TLS config by the client

    // when
    try (zeebe) {
      zeebe.start();

      // then
      SslAssert.assertThat(
              new InetSocketAddress(zeebe.getExternalHost(), zeebe.getMappedPort(8080)))
          .isSecuredBy(certificate);
      SslAssert.assertThat(
              new InetSocketAddress(
                  zeebe.getExternalHost(), zeebe.getMappedPort(ZeebePort.INTERNAL.getPort())))
          .isSecuredBy(certificate);
      SslAssert.assertThat(
              new InetSocketAddress(
                  zeebe.getExternalHost(), zeebe.getMappedPort(ZeebePort.COMMAND.getPort())))
          .isSecuredBy(certificate);
      SslAssert.assertThat(
              new InetSocketAddress(
                  zeebe.getExternalHost(), zeebe.getMappedPort(ZeebePort.GATEWAY.getPort())))
          .isSecuredBy(certificate);
    }
  }

  private File convertToPkcs1(final PrivateKey key, final File dest) throws IOException {
    final var encoded = key.getEncoded();
    final var pkcs1Key = PrivateKeyInfo.getInstance(encoded).parsePrivateKey().toASN1Primitive();
    final var pkcs1Encoded = pkcs1Key.getEncoded();
    final var pemObject = new PemObject("RSA PRIVATE KEY", pkcs1Encoded);
    final var pemWriter = new PemWriter(new FileWriter(dest));

    pemWriter.writeObject(pemObject);
    pemWriter.close();

    return dest;
  }
}
