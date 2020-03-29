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
package io.atomix.core.test;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.core.test.messaging.TestBroadcastServiceFactory;
import io.atomix.core.test.messaging.TestMessagingServiceFactory;
import io.atomix.core.test.messaging.TestUnicastServiceFactory;
import io.atomix.utils.net.Address;
import java.util.concurrent.atomic.AtomicInteger;

/** Test Atomix factory. */
public class TestAtomixFactory {
  private final TestMessagingServiceFactory messagingServiceFactory =
      new TestMessagingServiceFactory();
  private final TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
  private final TestBroadcastServiceFactory broadcastServiceFactory =
      new TestBroadcastServiceFactory();
  private final AtomicInteger memberId = new AtomicInteger();

  /**
   * Returns a new Atomix instance.
   *
   * @return a new Atomix instance
   */
  public Atomix newInstance() {
    final int id = memberId.incrementAndGet();
    return new TestAtomix(
        MemberId.from(String.valueOf(id)),
        Address.from("localhost", 5000 + id),
        messagingServiceFactory,
        unicastServiceFactory,
        broadcastServiceFactory);
  }
}
