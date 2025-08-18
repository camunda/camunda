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
package io.camunda.spring.client.properties;

import java.nio.file.Path;

public class CamundaClientAuthClientAssertionProperties {
  /** The path to the keystore where the client assertion certificate is stored. */
  private Path keystorePath;

  /** The password of the referenced keystore. */
  private String keystorePassword;

  /**
   * The alias of the key containing the certificate used to sign the client assertion certificate.
   * If not set, the first alias from the keystore is used.
   */
  private String keystoreKeyAlias;

  /** The password of the key referenced by the alias. If not set, the keystore password is used. */
  private String keystoreKeyPassword;

  public Path getKeystorePath() {
    return keystorePath;
  }

  public void setKeystorePath(final Path keystorePath) {
    this.keystorePath = keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(final String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public String getKeystoreKeyAlias() {
    return keystoreKeyAlias;
  }

  public void setKeystoreKeyAlias(final String keystoreKeyAlias) {
    this.keystoreKeyAlias = keystoreKeyAlias;
  }

  public String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  public void setKeystoreKeyPassword(final String keystoreKeyPassword) {
    this.keystoreKeyPassword = keystoreKeyPassword;
  }
}
