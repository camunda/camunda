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
package io.atomix.primitive.protocol;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionService;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.primitive.service.ServiceConfig;

/** State machine replication-based primitive protocol. */
public interface ProxyProtocol extends PrimitiveProtocol {

  /**
   * Returns the protocol partition group name.
   *
   * @return the protocol partition group name
   */
  String group();

  /**
   * Returns a new primitive proxy for the given partition group.
   *
   * @param primitiveName the primitive name
   * @param primitiveType the primitive type
   * @param serviceType the primitive service type
   * @param serviceConfig the service configuration
   * @param partitionService the partition service
   * @return the proxy for the given partition group
   */
  <S> ProxyClient<S> newProxy(
      String primitiveName,
      PrimitiveType primitiveType,
      Class<S> serviceType,
      ServiceConfig serviceConfig,
      PartitionService partitionService);
}
