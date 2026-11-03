/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import java.sql.SQLException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;

class TranslatingMapperProxyTest {

  @Test
  void shouldReturnDelegateResult() {
    // given
    final var mapper = TranslatingMapperProxy.wrap(TestMapper.class, new FailingMapper(null));

    // when
    final var result = mapper.read();

    // then
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void shouldTranslateKnownDatabaseError() {
    // given
    final var failure = new PersistenceException(new SQLException("down", "08001"));
    final var mapper = TranslatingMapperProxy.wrap(TestMapper.class, new FailingMapper(failure));

    // when / then
    assertThatThrownBy(mapper::read)
        .isInstanceOfSatisfying(
            CamundaSearchException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.CONNECTION_FAILED))
        .hasCause(failure);
  }

  @Test
  void shouldRethrowUnrelatedExceptionUnchanged() {
    // given
    final var failure = new IllegalStateException("boom");
    final var mapper = TranslatingMapperProxy.wrap(TestMapper.class, new FailingMapper(failure));

    // when / then
    assertThatThrownBy(mapper::read).isSameAs(failure);
  }

  interface TestMapper {
    String read();
  }

  record FailingMapper(RuntimeException failure) implements TestMapper {

    @Override
    public String read() {
      if (failure != null) {
        throw failure;
      }
      return "ok";
    }
  }
}
