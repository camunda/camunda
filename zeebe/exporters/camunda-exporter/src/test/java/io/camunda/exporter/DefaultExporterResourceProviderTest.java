/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.auditlog.AuditLogHandler;
import io.camunda.exporter.handlers.auditlog.AuthorizationAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.BatchOperationCreationAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.BatchOperationLifecycleManagementAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.DecisionEvaluationAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.IncidentResolutionAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.MappingRuleAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.ProcessInstanceCancelAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.ProcessInstanceCreationAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.ProcessInstanceMigrationAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.ProcessInstanceModificationAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.ResourceAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.TenantAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.TenantEntityAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.UserAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.UserTaskAuditLogTransformer;
import io.camunda.exporter.handlers.auditlog.VariableAddUpdateAuditLogTransformer;
import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedItemHandler;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class DefaultExporterResourceProviderTest {
  @ParameterizedTest
  @MethodSource("configProvider")
  void shouldHaveCorrectFullQualifiedNamesForIndexAndTemplates(final ExporterConfiguration config) {
    final var provider = new DefaultExporterResourceProvider();

    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    provider
        .getIndexDescriptors()
        .forEach(
            descriptor -> {
              final var name = descriptor.getFullQualifiedName();
              assertThat(name.matches(descriptor.getAllVersionsIndexNameRegexPattern())).isTrue();
              assertThat(
                      isValidIndexDescriptorName(descriptor, config.getConnect().getIndexPrefix()))
                  .isTrue();
            });

    provider
        .getIndexTemplateDescriptors()
        .forEach(
            descriptor ->
                assertThat(
                        isValidIndexTemplateDescriptorName(
                            descriptor, config.getConnect().getIndexPrefix()))
                    .isTrue());
  }

  @Test
  void shouldExportPendingBatchOperationItemsWhenConfigIsTrue() {
    // given
    final var config = new ExporterConfiguration();
    config.getBatchOperation().setExportItemsOnCreation(true);

    // when
    final var provider = new DefaultExporterResourceProvider();
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    // then
    assertThat(
            provider.getExportHandlers().stream()
                .filter(BatchOperationChunkCreatedItemHandler.class::isInstance)
                .toList())
        .hasSize(1);
  }

  @Test
  void shouldNotExportPendingBatchOperationItemsWhenConfigIsFalse() {
    // given
    final var config = new ExporterConfiguration();
    config.getBatchOperation().setExportItemsOnCreation(false);

    // when
    final var provider = new DefaultExporterResourceProvider();
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    // then
    assertThat(
            provider.getExportHandlers().stream()
                .filter(BatchOperationChunkCreatedItemHandler.class::isInstance)
                .toList())
        .isEmpty();
  }

  @Test
  void shouldNotAddSameHandlerMultipleTimes() {
    // given
    final var config = new ExporterConfiguration();
    config.getBatchOperation().setExportItemsOnCreation(true);

    // when
    final var provider = new DefaultExporterResourceProvider();
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    // then
    // AuditLogHandlers are excluded because they by design have multiple instances
    final var handlersExcludingAuditLog =
        provider.getExportHandlers().stream()
            .filter(handler -> !(handler instanceof AuditLogHandler))
            .toList();

    assertThat(handlersExcludingAuditLog)
        .hasSize(
            (int)
                handlersExcludingAuditLog.stream().map(ExportHandler::getClass).distinct().count());
  }

  @Test
  void shouldAddAuditLogHandlersFromAddAuditLogHandlersMethod() {
    // given
    final var config = new ExporterConfiguration();

    // when
    final var provider = new DefaultExporterResourceProvider();
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    // then
    final var auditLogHandlers =
        provider.getExportHandlers().stream().filter(AuditLogHandler.class::isInstance).toList();

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

    // Verify that all expected AuditLogHandler transformers are present
    assertThat(
            auditLogHandlers.stream()
                .collect(
                    Collectors.toMap(
                        exportHandler ->
                            (Class)
                                ((AuditLogHandler<?>) exportHandler).getTransformer().getClass(),
                        ExportHandler::getHandledValueType)))
        .containsExactlyInAnyOrderEntriesOf(expectedTransformers);

    assertThat(auditLogHandlers)
        .as(
            "Should have exactly "
                + expectedTransformers.size()
                + " AuditLogHandler instances added by addAuditLogHandlers method")
        .hasSize(expectedTransformers.size());
  }

  static Stream<ExporterConfiguration> configProvider() {
    final var configNoPrefix = new ExporterConfiguration();

    final var configWithPrefix = new ExporterConfiguration();
    configWithPrefix.getConnect().setIndexPrefix("global");

    final var configWithComponentNameAsPrefix = new ExporterConfiguration();
    configWithComponentNameAsPrefix.getConnect().setIndexPrefix("operate");

    return Stream.of(configNoPrefix, configWithPrefix, configWithComponentNameAsPrefix);
  }

  private boolean isValidIndexDescriptorName(
      final IndexDescriptor descriptor, final String prefix) {

    return Arrays.stream(ComponentNames.values())
        .map(componentName -> expectedName(descriptor, componentName.toString(), prefix) + "_")
        .anyMatch(
            possibleFullQualifiedName ->
                possibleFullQualifiedName.equals(descriptor.getFullQualifiedName()));
  }

  private boolean isValidIndexTemplateDescriptorName(
      final IndexTemplateDescriptor descriptor, final String prefix) {
    return Arrays.stream(ComponentNames.values())
        .map(
            componentName ->
                expectedName(descriptor, componentName.toString(), prefix) + "_template")
        .anyMatch(
            possibleTemplateName -> possibleTemplateName.equals(descriptor.getTemplateName()));
  }

  private String expectedName(
      final IndexDescriptor descriptor, final String componentName, final String prefix) {
    final var expectedName =
        new ArrayDeque<>(
            List.of(componentName, descriptor.getIndexName(), descriptor.getVersion()));

    if (!prefix.isEmpty()) {
      expectedName.addFirst(prefix);
    }

    return String.join("-", expectedName);
  }
}
