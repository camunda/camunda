/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging;

import io.atomix.utils.config.Config;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Messaging configuration. */
public class MessagingConfig implements Config {
  public static final int AUTO_SOCKET_SIZE = -11;
  private List<String> interfaces = new ArrayList<>();
  private Integer port;
  private Duration shutdownQuietPeriod = Duration.ofMillis(20);
  private Duration shutdownTimeout = Duration.ofSeconds(1);
  private boolean tlsEnabled = false;
  private File certificateChain;
  private File privateKey;
  private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NONE;
  private File keyStore;
  private String keyStorePassword;
  private int socketSendBuffer = AUTO_SOCKET_SIZE;
  private int socketReceiveBuffer = AUTO_SOCKET_SIZE;
  private Duration heartbeatTimeout = Duration.ofSeconds(15);
  private Duration heartbeatInterval = Duration.ofSeconds(5);

  /**
   * Returns the local interfaces to which to bind the node.
   *
   * @return the local interfaces to which to bind the node
   */
  public List<String> getInterfaces() {
    return interfaces;
  }

  /**
   * Sets the local interfaces to which to bind the node.
   *
   * @param interfaces the local interfaces to which to bind the node
   * @return this config for chaining
   */
  public MessagingConfig setInterfaces(final List<String> interfaces) {
    this.interfaces = interfaces;
    return this;
  }

  /**
   * Returns the local port to which to bind the node.
   *
   * @return the local port to which to bind the node
   */
  public Integer getPort() {
    return port;
  }

  /**
   * Sets the local port to which to bind the node.
   *
   * @param port the local port to which to bind the node
   * @return this config for chaining
   */
  public MessagingConfig setPort(final Integer port) {
    this.port = port;
    return this;
  }

  /**
   * @return the configured shutdown quiet period
   */
  public Duration getShutdownQuietPeriod() {
    return shutdownQuietPeriod;
  }

  /**
   * Sets the shutdown quiet period. This is mostly useful to set a small value when testing,
   * otherwise every tests takes an additional 2 second just to shutdown the executor.
   *
   * @param shutdownQuietPeriod the quiet period on shutdown
   * @return this config for chaining
   */
  public MessagingConfig setShutdownQuietPeriod(final Duration shutdownQuietPeriod) {
    this.shutdownQuietPeriod = shutdownQuietPeriod;
    return this;
  }

  /**
   * @return the configured shutdown timeout
   */
  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  /**
   * Sets the shutdown timeout.
   *
   * @param shutdownTimeout the time to wait for an orderly shutdown of the messaging service
   * @return this config for chaining
   */
  public MessagingConfig setShutdownTimeout(final Duration shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
    return this;
  }

  /**
   * @return true if TLS is enabled for inter-cluster communication
   */
  public boolean isTlsEnabled() {
    return tlsEnabled;
  }

  /**
   * Sets whether or not to enable TLS for inter-cluster communication.
   *
   * @param tlsEnabled true to enable TLS between all nodes, false otherwise
   * @return this config for chaining
   */
  public MessagingConfig setTlsEnabled(final boolean tlsEnabled) {
    this.tlsEnabled = tlsEnabled;
    return this;
  }

  public CompressionAlgorithm getCompressionAlgorithm() {
    return compressionAlgorithm;
  }

  public MessagingConfig setCompressionAlgorithm(final CompressionAlgorithm algorithm) {
    compressionAlgorithm = algorithm;
    return this;
  }

  /**
   * The certificate chain to use for inter-cluster communication. This certificate is used for both
   * the server and the client.
   *
   * @return a file which contains the certificate chain
   */
  public File getCertificateChain() {
    return certificateChain;
  }

  /**
   * Sets the certificate chain to use for inter-cluster communication. If using a self-signed
   * certificate, or one which is not widely trusted, this must be the complete chain.
   *
   * <p>Mandatory if TLS is enabled.
   *
   * @param certificateChain a file containing the certificate chain
   * @return this config for chaining
   * @throws IllegalArgumentException if the certificate chain is null
   * @throws IllegalArgumentException if the certificate chain points to a file which does not exist
   * @throws IllegalArgumentException if the certificate chain points to a file which cannot be read
   */
  public MessagingConfig setCertificateChain(final File certificateChain) {
    if (certificateChain == null) {
      throw new IllegalArgumentException(
          "Expected a certificate chain in order to enable inter-cluster communication security, "
              + "but none given");
    }

    if (!certificateChain.canRead()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the node's inter-cluster communication certificate to be at %s, but either "
                  + "the file is missing or it is not readable",
              certificateChain));
    }

    this.certificateChain = certificateChain;
    return this;
  }

  /**
   * @return the private key of the certificate chain
   */
  public File getPrivateKey() {
    return privateKey;
  }

  /**
   * Sets the private key of the certificate chain.
   *
   * <p>Mandatory if TLS is enabled.
   *
   * @param privateKey the private key of the associated certificate chain
   * @return this config for chaining
   * @throws IllegalArgumentException if the private key is null
   * @throws IllegalArgumentException if the private key points to a file which does not exist
   * @throws IllegalArgumentException if the private key points to a file which cannot be read
   */
  public MessagingConfig setPrivateKey(final File privateKey) {
    if (privateKey == null) {
      throw new IllegalArgumentException(
          "Expected a private key in order to enable inter-cluster communication security, but none"
              + " given");
    }

    if (!privateKey.canRead()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the node's inter-cluster communication private key to be at %s, but either "
                  + "the file is missing or it is not readable",
              privateKey));
    }

    this.privateKey = privateKey;
    return this;
  }

  public MessagingConfig configureTls(
      final File keyStore,
      final String keyStorePassword,
      final File privateKey,
      final File certificateChain) {
    if (keyStore != null) {
      this.keyStore = keyStore;
      this.keyStorePassword = keyStorePassword;
    } else {
      setPrivateKey(privateKey);
      setCertificateChain(certificateChain);
    }
    return this;
  }

  public File getKeyStore() {
    return keyStore;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  /**
   * @return the configured size in bytes for SO_SNDBUF or `-1` if not configured.
   */
  public int getSocketSendBuffer() {
    return socketSendBuffer;
  }

  /**
   * Sets the size of SO_SNDBUF.GatewayCfgT
   *
   * @param socketSendBuffer the data size in bytes to use for SO_SNDBUF
   * @return this config for chaining
   */
  public MessagingConfig setSocketSendBuffer(final int socketSendBuffer) {
    this.socketSendBuffer = socketSendBuffer;
    return this;
  }

  /**
   * @return the configured size in bytes for SO_RCVBUF or `-1` if not configured.
   */
  public int getSocketReceiveBuffer() {
    return socketReceiveBuffer;
  }

  /**
   * Sets the size of SO_RCVBUF.
   *
   * @param socketReceiveBuffer the data size in bytes to use for SO_RCVBUF
   * @return this config for chaining
   */
  public MessagingConfig setSocketReceiveBuffer(final int socketReceiveBuffer) {
    this.socketReceiveBuffer = socketReceiveBuffer;
    return this;
  }

  public Duration getHeartbeatTimeout() {
    return heartbeatTimeout;
  }

  public MessagingConfig setHeartbeatTimeout(final Duration heartbeatTimeout) {
    this.heartbeatTimeout = heartbeatTimeout;
    return this;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public MessagingConfig setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
    return this;
  }

  public enum CompressionAlgorithm {
    GZIP,
    NONE,
    SNAPPY
  }
}
