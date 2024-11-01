/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.exporter.archiver.ArchiverRepository.NoopArchiverRepository;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@AutoCloseResources
final class ArchiverTest {
  @AutoCloseResource // ensures we always reap the thread no matter what
  private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

  @Nested
  final class CloseTest {
    private final CloseableRepository repository = new CloseableRepository();
    private final Archiver archiver =
        new Archiver(1, repository, LoggerFactory.getLogger(ArchiverTest.class), executor);

    @Test
    void shouldCloseExecutorOnClose() {
      // when
      archiver.close();

      // then
      assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void shouldCloseRepositoryOnClose() {
      // when
      archiver.close();

      // then
      assertThat(repository.isClosed).isTrue();
    }

    @Test
    void shouldNotThrowOnRepositoryCloseError() {
      // given
      repository.exception = new RuntimeException("foo");

      // when
      assertThatCode(archiver::close).doesNotThrowAnyException();
    }

    private static final class CloseableRepository extends NoopArchiverRepository {
      private boolean isClosed;
      private Exception exception;

      @Override
      public void close() throws Exception {
        if (exception != null) {
          throw exception;
        }

        isClosed = true;
      }
    }
  }
}
