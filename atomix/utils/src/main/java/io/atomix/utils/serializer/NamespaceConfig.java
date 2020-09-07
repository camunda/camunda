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
package io.atomix.utils.serializer;

import io.atomix.utils.config.Config;
import java.util.ArrayList;
import java.util.List;

/** Namespace configuration. */
public class NamespaceConfig implements Config {
  private String name = Namespace.NO_NAME;
  private boolean registrationRequired = true;
  private boolean compatible = false;
  private List<NamespaceTypeConfig> types = new ArrayList<>();

  /**
   * Returns the serializer name.
   *
   * @return the serializer name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the serializer name.
   *
   * @param name the serializer name
   * @return the serializer configuration
   */
  public NamespaceConfig setName(final String name) {
    this.name = name;
    return this;
  }

  /**
   * Returns whether registration is required.
   *
   * @return whether registration is required
   */
  public boolean isRegistrationRequired() {
    return registrationRequired;
  }

  /**
   * Sets whether registration is required.
   *
   * @param registrationRequired whether registration is required
   * @return the serializer configuration
   */
  public NamespaceConfig setRegistrationRequired(final boolean registrationRequired) {
    this.registrationRequired = registrationRequired;
    return this;
  }

  /**
   * Returns whether compatible serialization is enabled.
   *
   * @return whether compatible serialization is enabled
   */
  public boolean isCompatible() {
    return compatible;
  }

  /**
   * Sets whether compatible serialization is enabled.
   *
   * @param compatible whether compatible serialization is enabled
   * @return the serializer configuration
   */
  public NamespaceConfig setCompatible(final boolean compatible) {
    this.compatible = compatible;
    return this;
  }

  /**
   * Returns the serializable types.
   *
   * @return the serializable types
   */
  public List<NamespaceTypeConfig> getTypes() {
    return types;
  }

  /**
   * Sets the serializable types.
   *
   * @param types the serializable types
   * @return the serializer configuration
   */
  public NamespaceConfig setTypes(final List<NamespaceTypeConfig> types) {
    this.types = types;
    return this;
  }

  /**
   * Adds a serializable type to the configuration.
   *
   * @param type the serializable type to add
   * @return the serializer configuration
   */
  public NamespaceConfig addType(final NamespaceTypeConfig type) {
    types.add(type);
    return this;
  }
}
