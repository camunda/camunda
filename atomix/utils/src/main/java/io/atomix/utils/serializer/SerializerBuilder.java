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

import io.atomix.utils.Builder;

/** Serializer builder. */
public class SerializerBuilder implements Builder<Serializer> {
  private final String name;
  private final Namespace.Builder namespaceBuilder =
      Namespace.builder().register(Namespaces.BASIC).nextId(Namespaces.BEGIN_USER_CUSTOM_ID);

  public SerializerBuilder() {
    this(null);
  }

  public SerializerBuilder(final String name) {
    this.name = name;
  }

  /**
   * Requires explicit serializable type registration for serializable types.
   *
   * @return the serializer builder
   */
  public SerializerBuilder withRegistrationRequired() {
    return withRegistrationRequired(true);
  }

  /**
   * Sets whether serializable type registration is required for serializable types.
   *
   * @param registrationRequired whether serializable type registration is required for serializable
   *     types
   * @return the serializer builder
   */
  public SerializerBuilder withRegistrationRequired(final boolean registrationRequired) {
    namespaceBuilder.setRegistrationRequired(registrationRequired);
    return this;
  }

  /**
   * Enables compatible serialization for serializable types.
   *
   * @return the serializer builder
   */
  public SerializerBuilder withCompatibleSerialization() {
    return withCompatibleSerialization(true);
  }

  /**
   * Sets whether compatible serialization is enabled for serializable types.
   *
   * @param compatibleSerialization whether compatible serialization is enabled for user types
   * @return the serializer builder
   */
  public SerializerBuilder withCompatibleSerialization(final boolean compatibleSerialization) {
    namespaceBuilder.setCompatible(compatibleSerialization);
    return this;
  }

  /**
   * Adds a namespace to the serializer.
   *
   * @param namespace the namespace to add
   * @return the serializer builder
   */
  public SerializerBuilder withNamespace(final Namespace namespace) {
    namespaceBuilder.register(namespace);
    return this;
  }

  /**
   * Sets the serializable types.
   *
   * @param types the types to register
   * @return the serializer builder
   */
  public SerializerBuilder withTypes(final Class<?>... types) {
    namespaceBuilder.register(types);
    return this;
  }

  /**
   * Adds a serializable type to the builder.
   *
   * @param type the type to add
   * @return the serializer builder
   */
  public SerializerBuilder addType(final Class<?> type) {
    namespaceBuilder.register(type);
    return this;
  }

  /**
   * Adds a serializer to the builder.
   *
   * @param serializer the serializer to add
   * @param types the serializable types
   * @return the serializer builder
   */
  public SerializerBuilder addSerializer(
      final com.esotericsoftware.kryo.Serializer serializer, final Class<?>... types) {
    namespaceBuilder.register(serializer, types);
    return this;
  }

  @Override
  public Serializer build() {
    return Serializer.using(name != null ? namespaceBuilder.build(name) : namespaceBuilder.build());
  }
}
