/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.commit;
import static io.camunda.zeebe.db.writebehind.TestValues.held;
import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.DbValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WriterFailureTest {

  private WriteBehindStore<RawKey, HeldValue, DbValue> store;

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.close();
    }
  }

  @Test
  void shouldFailStoreWhenWriterThreadDiesOfUnexpectedError() {
    // given - the cache weighs a flushed value on the writer thread, after the storage write; an
    // error there is outside the retries of the write
    final var testThread = Thread.currentThread();
    final var storage = new InMemoryRawDbAccess();
    final var raw = new RawBackend(storage);
    @SuppressWarnings("unchecked")
    final var backend =
        (Backend<RawKey, HeldValue, DbValue>)
            Proxy.newProxyInstance(
                Backend.class.getClassLoader(),
                new Class<?>[] {Backend.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("weigh") && Thread.currentThread() != testThread) {
                    throw new OutOfMemoryError("expected failure of the writer");
                  }
                  try {
                    return method.invoke(raw, args);
                  } catch (final InvocationTargetException e) {
                    throw e.getCause();
                  }
                });
    store =
        new WriteBehindStore<>(
            backend, WriteBehindConfiguration.defaults().withFlushDelay(Duration.ZERO));

    // when
    commit(store, key(1), held(1));

    // then - without a failure, the committing thread would not notice the dead writer
    await()
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> commit(store, key(2), held(2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasRootCauseInstanceOf(OutOfMemoryError.class));
  }
}
