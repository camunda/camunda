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
package io.camunda.client.impl.util;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;

public final class SSLContextUtil {
  private SSLContextUtil() {}

  public static SSLSocketFactory createSSLContext(
      final Path keystorePath,
      final String keystorePassword,
      final Path truststorePath,
      final String truststorePassword,
      final String keystoreKeyPassword) {
    if (keystorePath == null && truststorePath == null) {
      return SSLContexts.createSystemDefault().getSocketFactory();
    }
    final SSLContextBuilder builder = SSLContexts.custom();
    try {
      if (keystorePath != null) {
        builder.loadKeyMaterial(
            keystorePath,
            keystorePassword == null ? null : keystorePassword.toCharArray(),
            keystoreKeyPassword == null ? new char[0] : keystoreKeyPassword.toCharArray());
      }
      if (truststorePath != null) {
        builder.loadTrustMaterial(
            truststorePath, truststorePassword == null ? null : truststorePassword.toCharArray());
      }
      return builder.build().getSocketFactory();
    } catch (final NoSuchAlgorithmException
        | KeyManagementException
        | KeyStoreException
        | UnrecoverableKeyException
        | CertificateException
        | IOException e) {
      throw new RuntimeException("Failed to create SSL context", e);
    }
  }
}
