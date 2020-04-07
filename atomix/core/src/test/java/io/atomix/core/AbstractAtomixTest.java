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
package io.atomix.core;

import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.MulticastDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.zeebe.test.util.socket.SocketUtil;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/** Base Atomix test. */
public abstract class AbstractAtomixTest {
  private static final int BASE_PORT = 5000;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private File dataDir;
  private Map<Integer, Address> addressMap;

  @Before
  public void setupAtomix() throws IOException {
    dataDir = temporaryFolder.newFolder();
    addressMap = new HashMap<>();
  }

  public File getDataDir() {
    return dataDir;
  }

  /** Creates an Atomix instance. */
  protected AtomixBuilder buildAtomix(
      final int id, final List<Integer> memberIds, final Properties properties) {
    final Collection<Node> nodes =
        memberIds.stream()
            .map(
                memberId -> {
                  final var address = getAddress(memberId);

                  return Node.builder()
                      .withId(String.valueOf(memberId))
                      .withAddress(address)
                      .build();
                })
            .collect(Collectors.toList());

    return Atomix.builder()
        .withClusterId("test")
        .withMemberId(String.valueOf(id))
        .withHost("localhost")
        .withPort(getAddress(id).port())
        .withProperties(properties)
        .withMulticastEnabled()
        .withMembershipProvider(
            !nodes.isEmpty()
                ? new BootstrapDiscoveryProvider(nodes)
                : new MulticastDiscoveryProvider());
  }

  private Address getAddress(final Integer memberId) {
    return addressMap.computeIfAbsent(
        memberId,
        newId -> {
          final var nextInetAddress = SocketUtil.getNextAddress();
          final var addressString = io.zeebe.util.SocketUtil.toHostAndPortString(nextInetAddress);
          return Address.from(addressString);
        });
  }

  /** Creates an Atomix instance. */
  protected Atomix createAtomix(
      final int id,
      final List<Integer> bootstrapIds,
      final Function<AtomixBuilder, Atomix> builderFunction) {
    return createAtomix(id, bootstrapIds, new Properties(), builderFunction);
  }

  /** Creates an Atomix instance. */
  protected Atomix createAtomix(
      final int id,
      final List<Integer> bootstrapIds,
      final Properties properties,
      final Function<AtomixBuilder, Atomix> builderFunction) {
    return builderFunction.apply(buildAtomix(id, bootstrapIds, properties));
  }
}
