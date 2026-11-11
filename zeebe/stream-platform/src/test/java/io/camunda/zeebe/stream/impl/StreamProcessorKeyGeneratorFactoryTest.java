/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.state.KeyGeneratorFactory;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

/**
 * A storage that keeps engine state elsewhere than RocksDB, like a relational database, replaces
 * the key generator, so that the keys commit with the state that uses them.
 */
final class StreamProcessorKeyGeneratorFactoryTest {

  @RegisterExtension
  private final StreamPlatformExtension streamPlatformExtension = new StreamPlatformExtension();

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldHandTheKeyGeneratorOfTheFactoryToTheRecordProcessors() {
    // given
    final var created = new AtomicReference<DbKeyGenerator>();
    final var usedContext = new AtomicReference<TransactionContext>();
    final KeyGeneratorFactory factory =
        (partitionId, zeebeDb, transactionContext) -> {
          usedContext.set(transactionContext);
          created.set(new DbKeyGenerator(partitionId, zeebeDb, transactionContext));
          return created.get();
        };

    // when
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(), true, builder -> builder.keyGeneratorFactory(factory));

    // then
    final var context = ArgumentCaptor.forClass(RecordProcessorContext.class);
    verify(streamPlatform.getDefaultMockedRecordProcessor(), timeout(5_000))
        .init(context.capture());
    assertThat(context.getValue().getKeyGenerator()).isSameAs(created.get());
    assertThat(context.getValue().getTransactionContext())
        .as("the key generator works on the transaction context of the state")
        .isSameAs(usedContext.get());
  }
}
