/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.zeebe.exporter.api.context.Context;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RdbmsExporterWrapperTest {

  @Test
  public void shouldFailWithInvalidConfiguration() {
    // given
    final var configuration = new ExporterConfiguration();
    configuration.setFlushInterval(Duration.ofMillis(-1000));
    final Context context = Mockito.mock(Context.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(context.getConfiguration().instantiate(Mockito.eq(ExporterConfiguration.class)))
        .thenReturn(configuration);

    final RdbmsExporterWrapper exporterWrapper =
        new RdbmsExporterWrapper(Mockito.mock(RdbmsService.class));

    // when
    assertThatThrownBy(() -> exporterWrapper.configure(context))
        .hasMessageContaining("flushInterval must be a positive duration");
  }
}
