/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.read.RdbmsTenantReaders;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RdbmsServiceFactoryTest {

  @ParameterizedTest
  @ValueSource(strings = {"a", "b"})
  void shouldRejectUnknownPhysicalTenant(final String physicalTenantId) {
    // given
    final Map<String, RdbmsMapperBundle> mapperBundle = Map.of("a", mock(RdbmsMapperBundle.class));
    final Map<String, RdbmsTenantReaders> readers = Map.of("b", mock(RdbmsTenantReaders.class));
    final var factory = new RdbmsServiceFactory(mapperBundle, readers, new SimpleMeterRegistry());

    // when / then
    assertThatThrownBy(() -> factory.createRdbmsService(physicalTenantId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing RDBMS")
        .hasMessageContaining("physical tenant '%s'".formatted(physicalTenantId));
  }
}
