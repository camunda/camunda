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

import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.core.AtomixConfig;
import io.atomix.core.AtomixRegistry;
import io.atomix.core.profile.ConsensusProfileConfig;
import io.atomix.core.test.messaging.TestBroadcastServiceFactory;
import io.atomix.core.test.messaging.TestMessagingServiceFactory;
import io.atomix.core.test.messaging.TestUnicastServiceFactory;
import io.atomix.utils.net.Address;
import java.util.Collections;

/** Test Atomix instance. */
public class TestAtomix extends Atomix {

  TestAtomix(
      final MemberId memberId,
      final Address address,
      final TestMessagingServiceFactory messagingServiceFactory,
      final TestUnicastServiceFactory unicastServiceFactory,
      final TestBroadcastServiceFactory broadcastServiceFactory) {
    super(
        config(memberId, address),
        AtomixRegistry.registry(),
        messagingServiceFactory.newMessagingService(address),
        unicastServiceFactory.newUnicastService(address),
        broadcastServiceFactory.newBroadcastService());
  }

  private static AtomixConfig config(final MemberId memberId, final Address address) {
    return new AtomixConfig()
        .setClusterConfig(
            new ClusterConfig()
                .setNodeConfig(new MemberConfig().setId(memberId).setAddress(address)))
        .setProfiles(Collections.singletonList(new ConsensusProfileConfig()));
  }
}
