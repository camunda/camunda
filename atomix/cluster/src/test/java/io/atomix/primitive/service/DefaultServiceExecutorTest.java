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
package io.atomix.primitive.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.TestPrimitiveType;
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.service.impl.DefaultCommit;
import io.atomix.primitive.service.impl.DefaultServiceExecutor;
import io.atomix.primitive.session.Session;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.time.WallClockTimestamp;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Default service executor test. */
public class DefaultServiceExecutorTest {
  @Test
  public void testExecuteOperations() throws Exception {
    final ServiceExecutor executor = executor();
    final Set<String> calls = new HashSet<>();

    executor.register(OperationId.command("a"), () -> calls.add("a"));
    executor.<Void>register(OperationId.command("b"), commit -> calls.add("b"));
    executor.register(
        OperationId.query("c"),
        commit -> {
          calls.add("c");
          return null;
        });
    executor.register(
        OperationId.query("d"),
        () -> {
          calls.add("d");
          return null;
        });
    executor.register(
        OperationId.command("e"),
        commit -> {
          calls.add("e");
          return commit.value();
        });

    executor.apply(commit(OperationId.command("a"), 1, null, System.currentTimeMillis()));
    assertTrue(calls.contains("a"));

    executor.apply(commit(OperationId.command("b"), 2, null, System.currentTimeMillis()));
    assertTrue(calls.contains("b"));

    executor.apply(commit(OperationId.query("c"), 3, null, System.currentTimeMillis()));
    assertTrue(calls.contains("c"));

    executor.apply(commit(OperationId.query("d"), 4, null, System.currentTimeMillis()));
    assertTrue(calls.contains("d"));

    executor.apply(commit(OperationId.command("e"), 5, null, System.currentTimeMillis()));
    assertTrue(calls.contains("e"));
  }

  @Test
  public void testScheduling() throws Exception {
    final ServiceExecutor executor = executor();
    executor.register(OperationId.command("a"), () -> {});
    executor.apply(commit(OperationId.command("a"), 1, null, 0));

    final Set<String> calls = new HashSet<>();
    executor.tick(new WallClockTimestamp(1));
    executor.schedule(Duration.ofMillis(100), () -> calls.add("a"));
    executor.tick(new WallClockTimestamp(100));
    assertFalse(calls.contains("a"));
    executor.tick(new WallClockTimestamp(101));
    assertTrue(calls.contains("a"));
  }

  private ServiceExecutor executor() {
    final ServiceContext context = mock(ServiceContext.class);
    when(context.serviceId()).thenReturn(PrimitiveId.from(1));
    when(context.serviceType()).thenReturn(TestPrimitiveType.instance());
    when(context.serviceName()).thenReturn("test");
    when(context.currentOperation()).thenReturn(OperationType.COMMAND);
    return new DefaultServiceExecutor(context, Serializer.using(Namespaces.BASIC));
  }

  @SuppressWarnings("unchecked")
  private <T> Commit<T> commit(
      final OperationId operation, final long index, final T value, final long timestamp) {
    return new DefaultCommit<T>(index, operation, value, mock(Session.class), timestamp);
  }
}
