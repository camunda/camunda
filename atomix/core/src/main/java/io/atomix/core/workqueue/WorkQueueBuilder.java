/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.core.workqueue;

import io.atomix.primitive.PrimitiveBuilder;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.ProxyCompatibleBuilder;
import io.atomix.primitive.protocol.ProxyProtocol;

/** Work queue builder. */
public abstract class WorkQueueBuilder<E>
    extends PrimitiveBuilder<WorkQueueBuilder<E>, WorkQueueConfig, WorkQueue<E>>
    implements ProxyCompatibleBuilder<WorkQueueBuilder<E>> {

  protected WorkQueueBuilder(
      final String name,
      final WorkQueueConfig config,
      final PrimitiveManagementService managementService) {
    super(WorkQueueType.instance(), name, config, managementService);
  }

  @Override
  public WorkQueueBuilder<E> withProtocol(final ProxyProtocol protocol) {
    return withProtocol((PrimitiveProtocol) protocol);
  }
}
