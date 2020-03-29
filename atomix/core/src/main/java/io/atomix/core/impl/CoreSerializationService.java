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
package io.atomix.core.impl;

import io.atomix.primitive.serialization.SerializationService;
import io.atomix.utils.serializer.SerializerBuilder;

/** Core serialization service. */
public class CoreSerializationService implements SerializationService {
  private final boolean registrationRequired;
  private final boolean compatibleSerialization;

  public CoreSerializationService(
      final boolean registrationRequired, final boolean compatibleSerialization) {
    this.registrationRequired = registrationRequired;
    this.compatibleSerialization = compatibleSerialization;
  }

  @Override
  public SerializerBuilder newBuilder() {
    return new SerializerBuilder()
        .withRegistrationRequired(registrationRequired)
        .withCompatibleSerialization(compatibleSerialization);
  }

  @Override
  public SerializerBuilder newBuilder(final String name) {
    return new SerializerBuilder(name)
        .withRegistrationRequired(registrationRequired)
        .withCompatibleSerialization(compatibleSerialization);
  }
}
