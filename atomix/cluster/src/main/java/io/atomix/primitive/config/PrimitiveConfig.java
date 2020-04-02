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
package io.atomix.primitive.config;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.protocol.PrimitiveProtocolConfig;
import io.atomix.utils.config.NamedConfig;
import io.atomix.utils.config.TypedConfig;
import io.atomix.utils.serializer.NamespaceConfig;

/** Primitive configuration. */
public abstract class PrimitiveConfig<C extends PrimitiveConfig<C>>
    implements TypedConfig<PrimitiveType>, NamedConfig<C> {
  private String name;
  private NamespaceConfig namespaceConfig;
  private PrimitiveProtocolConfig protocolConfig;
  private boolean readOnly = false;

  @Override
  public String getName() {
    return name;
  }

  @Override
  @SuppressWarnings("unchecked")
  public C setName(final String name) {
    this.name = name;
    return (C) this;
  }

  /**
   * Returns the serializer configuration.
   *
   * @return the serializer configuration
   */
  public NamespaceConfig getNamespaceConfig() {
    return namespaceConfig;
  }

  /**
   * Sets the serializer configuration.
   *
   * @param namespaceConfig the serializer configuration
   * @return the primitive configuration
   */
  @SuppressWarnings("unchecked")
  public C setNamespaceConfig(final NamespaceConfig namespaceConfig) {
    this.namespaceConfig = namespaceConfig;
    return (C) this;
  }

  /**
   * Returns the protocol configuration.
   *
   * @return the protocol configuration
   */
  public PrimitiveProtocolConfig getProtocolConfig() {
    return protocolConfig;
  }

  /**
   * Sets the protocol configuration.
   *
   * @param protocolConfig the protocol configuration
   * @return the primitive configuration
   */
  @SuppressWarnings("unchecked")
  public C setProtocolConfig(final PrimitiveProtocolConfig protocolConfig) {
    this.protocolConfig = protocolConfig;
    return (C) this;
  }

  /**
   * Sets the primitive to read-only.
   *
   * @return the primitive configuration
   */
  public C setReadOnly() {
    return setReadOnly(true);
  }

  /**
   * Returns whether the primitive is read-only.
   *
   * @return whether the primitive is read-only
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Sets whether the primitive is read-only.
   *
   * @param readOnly whether the primitive is read-only
   * @return the primitive configuration
   */
  @SuppressWarnings("unchecked")
  public C setReadOnly(final boolean readOnly) {
    this.readOnly = readOnly;
    return (C) this;
  }
}
