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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Messaging configuration. */
public class MessagingConfig implements Config {
  private List<String> interfaces = new ArrayList<>();
  private Integer port;
  private int connectionPoolSize = 8;
  private Duration connectTimeout = Duration.ofSeconds(10);
  private TlsConfig tlsConfig = new TlsConfig();

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
   * @return the local cluster configuration
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
   * @return the local cluster configuration
   */
  public MessagingConfig setPort(final Integer port) {
    this.port = port;
    return this;
  }

  /**
   * Returns the connection pool size.
   *
   * @return the connection pool size
   */
  public int getConnectionPoolSize() {
    return connectionPoolSize;
  }

  /**
   * Sets the connection pool size.
   *
   * @param connectionPoolSize the connection pool size
   * @return the messaging configuration
   */
  public MessagingConfig setConnectionPoolSize(final int connectionPoolSize) {
    this.connectionPoolSize = connectionPoolSize;
    return this;
  }

  /**
   * Returns the Netty connection timeout.
   *
   * @return the Netty connection timeout
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Sets the Netty connection timeout.
   *
   * @param connectTimeout the Netty connection timeout
   * @return the messaging configuration
   */
  public MessagingConfig setConnectTimeout(final Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  /**
   * Returns the TLS configuration.
   *
   * @return the TLS configuration
   */
  public TlsConfig getTlsConfig() {
    return tlsConfig;
  }

  /**
   * Sets the TLS configuration.
   *
   * @param tlsConfig the TLS configuration
   * @return the messaging configuration
   */
  public MessagingConfig setTlsConfig(final TlsConfig tlsConfig) {
    this.tlsConfig = tlsConfig;
    return this;
  }
}
