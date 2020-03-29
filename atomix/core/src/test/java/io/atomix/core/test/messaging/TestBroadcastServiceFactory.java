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
package io.atomix.core.test.messaging;

import com.google.common.collect.Sets;
import io.atomix.cluster.messaging.ManagedBroadcastService;
import java.util.Set;

/** Test broadcast service factory. */
public class TestBroadcastServiceFactory {
  private final Set<TestBroadcastService> services = Sets.newCopyOnWriteArraySet();

  /**
   * Returns a new test broadcast service for the given endpoint.
   *
   * @return the broadcast service for the given endpoint
   */
  public ManagedBroadcastService newBroadcastService() {
    return new TestBroadcastService(services);
  }
}
