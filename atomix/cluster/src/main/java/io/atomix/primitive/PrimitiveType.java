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
package io.atomix.primitive;

import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.utils.ConfiguredType;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;

/** Primitive type. */
public interface PrimitiveType<
        B extends PrimitiveBuilder, C extends PrimitiveConfig, P extends SyncPrimitive>
    extends ConfiguredType<C> {

  /**
   * Returns the primitive type namespace.
   *
   * @return the primitive type namespace
   */
  default Namespace namespace() {
    return Namespace.builder().register(Namespaces.BASIC).register(ServiceConfig.class).build();
  }

  /**
   * Returns a new instance of the primitive configuration.
   *
   * @return a new instance of the primitive configuration
   */
  @Override
  C newConfig();

  /**
   * Returns a new primitive builder.
   *
   * @param primitiveName the primitive name
   * @param config the primitive configuration
   * @param managementService the primitive management service
   * @return a new primitive builder
   */
  B newBuilder(String primitiveName, C config, PrimitiveManagementService managementService);

  /**
   * Creates a new service instance from the given configuration.
   *
   * @param config the service configuration
   * @return the service instance
   */
  PrimitiveService newService(ServiceConfig config);
}
