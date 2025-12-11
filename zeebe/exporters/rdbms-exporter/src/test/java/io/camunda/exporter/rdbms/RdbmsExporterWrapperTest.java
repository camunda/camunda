/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.exporter.rdbms.handlers.auditlog.AuditLogExportHandler;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        new RdbmsExporterWrapper(
            Mockito.mock(RdbmsService.class), Mockito.mock(VendorDatabaseProperties.class));

    // when
    assertThatThrownBy(() -> exporterWrapper.configure(context))
        .hasMessageContaining("flushInterval must be a positive duration");
  }

  @Test
  public void shouldRegisterAuditLogHandlers() {
    // given
    final var configuration = new ExporterConfiguration();
    final Context context = Mockito.mock(Context.class, Mockito.RETURNS_DEEP_STUBS);
    final RdbmsService rdbmsService = Mockito.mock(RdbmsService.class, Mockito.RETURNS_DEEP_STUBS);
    final RdbmsWriter rdbmsWriter = Mockito.mock(RdbmsWriter.class, Mockito.RETURNS_DEEP_STUBS);

    Mockito.when(context.getConfiguration().instantiate(Mockito.eq(ExporterConfiguration.class)))
        .thenReturn(configuration);
    Mockito.when(context.getPartitionId()).thenReturn(1);
    Mockito.when(rdbmsService.createWriter(any(RdbmsWriterConfig.class))).thenReturn(rdbmsWriter);

    final RdbmsExporterWrapper exporterWrapper =
        new RdbmsExporterWrapper(rdbmsService, Mockito.mock(VendorDatabaseProperties.class));

    // when
    exporterWrapper.configure(context);

    // then - verify that audit log handlers are registered
    final var registeredHandlers = exporterWrapper.getExporter().getRegisteredHandlers();

    final Set<ValueType> expectedRegisteredTransformers =
        Set.of(
            ValueType.BATCH_OPERATION_CREATION,
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            ValueType.PROCESS_INSTANCE_MODIFICATION);

    // Check that all expected AuditLogExportHandlers are registered
    assertAuditLogExportPresent(registeredHandlers, expectedRegisteredTransformers);

    // Verify that exactly 2 audit log handlers are registered
    final long auditLogHandlerCount =
        registeredHandlers.values().stream()
            .flatMap(java.util.List::stream)
            .filter(AuditLogExportHandler.class::isInstance)
            .count();

    assertThat(auditLogHandlerCount)
        .as("Should have exactly 3 audit log handlers registered")
        .isEqualTo(expectedRegisteredTransformers.size());
  }

  private void assertAuditLogExportPresent(
      final Map<ValueType, List<RdbmsExportHandler>> registeredHandlers,
      final Set<?> batchOperationCreation) {
    assertThat(registeredHandlers)
        .containsKey(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT)
        .extracting(map -> map.get(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT))
        .satisfies(
            handlers ->
                assertThat(handlers)
                    .isNotEmpty()
                    .anySatisfy(
                        handler -> assertThat(handler).isInstanceOf(AuditLogExportHandler.class)));
  }
}
