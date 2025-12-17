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

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.exporter.rdbms.handlers.auditlog.AuditLogExportHandler;
import io.camunda.exporter.rdbms.handlers.auditlog.AuthorizationAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.BatchOperationCreationAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.BatchOperationLifecycleManagementAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.DecisionEvaluationAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.IncidentResolutionAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.MappingRuleAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.ProcessInstanceCancelAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.ProcessInstanceCreationAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.ProcessInstanceMigrationAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.ProcessInstanceModificationAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.ResourceAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.TenantAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.TenantEntityAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.UserAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.UserTaskAuditLogTransformer;
import io.camunda.exporter.rdbms.handlers.auditlog.VariableAddUpdateAuditLogTransformer;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            Mockito.mock(RdbmsService.class),
            Mockito.mock(LiquibaseSchemaManager.class),
            Mockito.mock(VendorDatabaseProperties.class));

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
    final RdbmsWriters rdbmsWriters = Mockito.mock(RdbmsWriters.class, Mockito.RETURNS_DEEP_STUBS);

    Mockito.when(context.getConfiguration().instantiate(Mockito.eq(ExporterConfiguration.class)))
        .thenReturn(configuration);
    Mockito.when(context.getPartitionId()).thenReturn(1);
    Mockito.when(rdbmsService.createWriter(any(RdbmsWriterConfig.class))).thenReturn(rdbmsWriters);

    final RdbmsExporterWrapper exporterWrapper =
        new RdbmsExporterWrapper(
            rdbmsService,
            Mockito.mock(LiquibaseSchemaManager.class),
            Mockito.mock(VendorDatabaseProperties.class));

    // when
    exporterWrapper.configure(context);

    // then - verify that audit log handlers are registered
    final var registeredHandlers = exporterWrapper.getExporter().getRegisteredHandlers();

    final Map<Class<?>, ValueType> expectedTransformers =
        Map.ofEntries(
            Map.entry(AuthorizationAuditLogTransformer.class, ValueType.AUTHORIZATION),
            Map.entry(
                BatchOperationCreationAuditLogTransformer.class,
                ValueType.BATCH_OPERATION_CREATION),
            Map.entry(
                BatchOperationLifecycleManagementAuditLogTransformer.class,
                ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT),
            Map.entry(DecisionEvaluationAuditLogTransformer.class, ValueType.DECISION_EVALUATION),
            Map.entry(IncidentResolutionAuditLogTransformer.class, ValueType.INCIDENT),
            Map.entry(MappingRuleAuditLogTransformer.class, ValueType.MAPPING_RULE),
            Map.entry(ProcessInstanceCancelAuditLogTransformer.class, ValueType.PROCESS_INSTANCE),
            Map.entry(
                ProcessInstanceCreationAuditLogTransformer.class,
                ValueType.PROCESS_INSTANCE_CREATION),
            Map.entry(
                ProcessInstanceMigrationAuditLogTransformer.class,
                ValueType.PROCESS_INSTANCE_MIGRATION),
            Map.entry(
                ProcessInstanceModificationAuditLogTransformer.class,
                ValueType.PROCESS_INSTANCE_MODIFICATION),
            Map.entry(ResourceAuditLogTransformer.class, ValueType.RESOURCE),
            Map.entry(TenantAuditLogTransformer.class, ValueType.TENANT),
            Map.entry(TenantEntityAuditLogTransformer.class, ValueType.TENANT),
            Map.entry(UserAuditLogTransformer.class, ValueType.USER),
            Map.entry(UserTaskAuditLogTransformer.class, ValueType.USER_TASK),
            Map.entry(VariableAddUpdateAuditLogTransformer.class, ValueType.VARIABLE));

    // Check that all expected AuditLogExportHandlers are registered
    assertAuditLogExportPresent(registeredHandlers, expectedTransformers);

    // Verify that the exact number audit log handlers are registered
    final long auditLogHandlerCount =
        registeredHandlers.values().stream()
            .flatMap(java.util.List::stream)
            .filter(AuditLogExportHandler.class::isInstance)
            .count();

    assertThat(auditLogHandlerCount)
        .as("Should have exactly " + expectedTransformers.size() + " audit log handlers registered")
        .isEqualTo(expectedTransformers.size());
  }

  private void assertAuditLogExportPresent(
      final Map<ValueType, List<RdbmsExportHandler>> registeredHandlers,
      final Map<Class<?>, ValueType> expectedRegisteredTransformers) {
    final Map<Class<?>, ValueType> actualRegisteredHandlers = new HashMap<>();
    registeredHandlers.forEach(
        (valueType, handlers) -> {
          for (final RdbmsExportHandler<?> handler : handlers) {
            if (handler instanceof AuditLogExportHandler<?>) {
              actualRegisteredHandlers.put(
                  ((AuditLogExportHandler<?>) handler).getTransformer().getClass(), valueType);
            }
          }
        });
    assertThat(actualRegisteredHandlers)
        .as("Audit log handlers should match expected handlers")
        .containsExactlyInAnyOrderEntriesOf(expectedRegisteredTransformers);
  }
}
