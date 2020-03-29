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

import io.atomix.core.test.TestAtomixFactory;
import io.atomix.core.test.protocol.TestProtocol;
import io.atomix.primitive.protocol.ProxyProtocol;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;

/** Base Atomix test. */
public abstract class AbstractPrimitiveTest {
  private List<Atomix> members;
  private TestAtomixFactory atomixFactory;
  private TestProtocol protocol;

  /**
   * Returns the primitive protocol with which to test.
   *
   * @return the protocol with which to test
   */
  protected ProxyProtocol protocol() {
    return protocol;
  }

  /**
   * Returns a new Atomix instance.
   *
   * @return a new Atomix instance.
   */
  protected Atomix atomix() throws Exception {
    final Atomix instance = createAtomix();
    instance.start().get(30, TimeUnit.SECONDS);
    return instance;
  }

  /**
   * Creates a new Atomix instance.
   *
   * @return the Atomix instance
   */
  private Atomix createAtomix() {
    final Atomix atomix = atomixFactory.newInstance();
    members.add(atomix);
    return atomix;
  }

  @Before
  public void setupTest() throws Exception {
    members = new ArrayList<>();
    atomixFactory = new TestAtomixFactory();
    protocol = TestProtocol.builder().withNumPartitions(3).build();
  }

  @After
  public void teardownTest() throws Exception {
    final List<CompletableFuture<Void>> futures =
        members.stream().map(Atomix::stop).collect(Collectors.toList());
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
          .get(30, TimeUnit.SECONDS);
    } catch (final Exception e) {
      // Do nothing
    } finally {
      protocol.close();
    }
  }
}
