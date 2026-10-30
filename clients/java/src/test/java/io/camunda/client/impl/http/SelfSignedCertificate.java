/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * A throwaway key pair for localhost, created with the JDK's keytool, so no key is committed. Lets
 * tests run a local TLS server, which the client needs to talk HTTP/2.
 */
final class SelfSignedCertificate {

  private static final char[] PASSWORD = "changeit".toCharArray();
  private static final long KEYTOOL_TIMEOUT_SECONDS = 60;

  private final Path directory;
  private final Path keyStore;
  private final Path certificate;

  private SelfSignedCertificate(final Path directory) {
    this.directory = directory;
    keyStore = directory.resolve("server.p12");
    certificate = directory.resolve("server.pem");
  }

  /** Creates the key store and the matching certificate in the given directory. */
  static SelfSignedCertificate create(final Path directory) throws Exception {
    final SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate(directory);
    selfSignedCertificate.generate();
    return selfSignedCertificate;
  }

  /** The certificate in PEM format, for the client to trust. */
  Path certificatePath() {
    return certificate;
  }

  /** An SSL context that presents the certificate, for the server. */
  SSLContext serverSslContext() throws Exception {
    final KeyStore store = KeyStore.getInstance("PKCS12");
    try (final InputStream in = Files.newInputStream(keyStore)) {
      store.load(in, PASSWORD);
    }
    final KeyManagerFactory factory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(store, PASSWORD);
    final SSLContext context = SSLContext.getInstance("TLS");
    context.init(factory.getKeyManagers(), null, null);
    return context;
  }

  private void generate() throws Exception {
    final Path binDirectory = Paths.get(System.getProperty("java.home"), "bin");
    final Path keytool =
        Files.exists(binDirectory.resolve("keytool.exe"))
            ? binDirectory.resolve("keytool.exe")
            : binDirectory.resolve("keytool");
    if (!Files.exists(keytool)) {
      fail("keytool is needed to create the test certificate but is not in %s", binDirectory);
    }
    run(
        keytool.toString(),
        "-genkeypair",
        "-alias",
        "server",
        "-keyalg",
        "RSA",
        "-keysize",
        "2048",
        "-dname",
        "CN=localhost",
        "-ext",
        "san=dns:localhost",
        "-validity",
        "1",
        "-storetype",
        "PKCS12",
        "-keystore",
        keyStore.toString(),
        "-storepass",
        new String(PASSWORD));
    run(
        keytool.toString(),
        "-exportcert",
        "-rfc",
        "-alias",
        "server",
        "-keystore",
        keyStore.toString(),
        "-storepass",
        new String(PASSWORD),
        "-file",
        certificate.toString());
  }

  private void run(final String... command) throws Exception {
    final Path log = Files.createTempFile(directory, "keytool", ".log");
    final Process process =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    if (!process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("keytool did not finish within %d seconds", KEYTOOL_TIMEOUT_SECONDS);
    }
    assertThat(process.exitValue())
        .describedAs(new String(Files.readAllBytes(log), StandardCharsets.UTF_8))
        .isZero();
  }
}
