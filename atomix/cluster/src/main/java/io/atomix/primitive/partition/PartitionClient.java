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
package io.atomix.primitive.partition;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;

/** Primitive client. */
public interface PartitionClient {

  /**
   * Returns a new session builder for the given primitive type.
   *
   * @param primitiveName the proxy name
   * @param primitiveType the type for which to return a new proxy builder
   * @param serviceConfig the primitive service configuration
   * @return a new proxy builder for the given primitive type
   */
  SessionClient.Builder sessionBuilder(
      String primitiveName, PrimitiveType primitiveType, ServiceConfig serviceConfig);
}
