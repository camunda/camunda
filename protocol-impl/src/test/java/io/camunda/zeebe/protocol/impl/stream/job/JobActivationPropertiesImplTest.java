/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.List;

final class JobActivationPropertiesImplTest {
  @RegressionTest("https://github.com/camunda/zeebe/issues/14624")
  void shouldIterateTenantsImmutably() {
    // given
    final var properties = new JobActivationPropertiesImpl().setTenantIds(List.of("foo", "bar"));
    final var firstIterator = properties.tenantIds().iterator();
    final var secondIterator = properties.tenantIds().iterator();

    // when
    firstIterator.next();
    final var foo = secondIterator.next();
    final var bar = firstIterator.next();

    // then
    assertThat(foo).isEqualTo("foo");
    assertThat(bar).isEqualTo("bar");
  }
}
