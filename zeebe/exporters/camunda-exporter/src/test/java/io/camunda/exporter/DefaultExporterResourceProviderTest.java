/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.DefaultExporterResourceProvider.PROCESS_DEFINITION_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.AuditLogHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedItemHandler;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuthorizationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.BatchOperationCreationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.BatchOperationLifecycleManagementAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.DecisionAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.DecisionEvaluationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.DecisionRequirementsRecordAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.FormAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.GroupAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.GroupEntityAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.IncidentResolutionAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.MappingRuleAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceCancelAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceCreationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceMigrationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceModificationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ResourceAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.RoleAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.RoleEntityAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.TenantAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.TenantEntityAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.UserAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.UserTaskAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.VariableAddUpdateAuditLogTransformer;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DefaultExporterResourceProviderTest {
  @ParameterizedTest
  @MethodSource("configProvider")
  void shouldHaveCorrectFullQualifiedNamesForIndexAndTemplates(final ExporterConfiguration config) {
    final var provider = new DefaultExporterResourceProvider();

    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new ExporterTestContext(),
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
        new ExporterTestContext(),
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
        new ExporterTestContext(),
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
        new ExporterTestContext(),
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

  @ParameterizedTest
  @MethodSource("expectedAuditLogTransformers")
  void shouldAddAuditLogHandlersFromAddAuditLogHandlersMethod(
      final int partitionId, final Map<Class<?>, ValueType> expectedTransformers) {
    // given
    final var config = new ExporterConfiguration();
    final var context = new ExporterTestContext();
    context.setPartitionId(partitionId);

    // when
    final var provider = new DefaultExporterResourceProvider();
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        context,
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    // then
    final var auditLogHandlers =
        provider.getExportHandlers().stream().filter(AuditLogHandler.class::isInstance).toList();

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

  private static Stream<Arguments> expectedAuditLogTransformers() {
    return Stream.of(
        Arguments.arguments(
            PROCESS_DEFINITION_PARTITION,
            Map.ofEntries(
                Map.entry(AuthorizationAuditLogTransformer.class, ValueType.AUTHORIZATION),
                Map.entry(
                    BatchOperationCreationAuditLogTransformer.class,
                    ValueType.BATCH_OPERATION_CREATION),
                Map.entry(
                    BatchOperationLifecycleManagementAuditLogTransformer.class,
                    ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT),
                Map.entry(DecisionAuditLogTransformer.class, ValueType.DECISION),
                Map.entry(
                    DecisionEvaluationAuditLogTransformer.class, ValueType.DECISION_EVALUATION),
                Map.entry(
                    DecisionRequirementsRecordAuditLogTransformer.class,
                    ValueType.DECISION_REQUIREMENTS),
                Map.entry(FormAuditLogTransformer.class, ValueType.FORM),
                Map.entry(GroupAuditLogTransformer.class, ValueType.GROUP),
                Map.entry(GroupEntityAuditLogTransformer.class, ValueType.GROUP),
                Map.entry(IncidentResolutionAuditLogTransformer.class, ValueType.INCIDENT),
                Map.entry(MappingRuleAuditLogTransformer.class, ValueType.MAPPING_RULE),
                Map.entry(ProcessAuditLogTransformer.class, ValueType.PROCESS),
                Map.entry(
                    ProcessInstanceCancelAuditLogTransformer.class, ValueType.PROCESS_INSTANCE),
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
                Map.entry(RoleAuditLogTransformer.class, ValueType.ROLE),
                Map.entry(RoleEntityAuditLogTransformer.class, ValueType.ROLE),
                Map.entry(TenantAuditLogTransformer.class, ValueType.TENANT),
                Map.entry(TenantEntityAuditLogTransformer.class, ValueType.TENANT),
                Map.entry(UserAuditLogTransformer.class, ValueType.USER),
                Map.entry(UserTaskAuditLogTransformer.class, ValueType.USER_TASK),
                Map.entry(VariableAddUpdateAuditLogTransformer.class, ValueType.VARIABLE))),
        Arguments.arguments(
            2,
            Map.ofEntries(
                Map.entry(
                    BatchOperationLifecycleManagementAuditLogTransformer.class,
                    ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT),
                Map.entry(
                    DecisionEvaluationAuditLogTransformer.class, ValueType.DECISION_EVALUATION),
                Map.entry(IncidentResolutionAuditLogTransformer.class, ValueType.INCIDENT),
                Map.entry(
                    ProcessInstanceCancelAuditLogTransformer.class, ValueType.PROCESS_INSTANCE),
                Map.entry(
                    ProcessInstanceCreationAuditLogTransformer.class,
                    ValueType.PROCESS_INSTANCE_CREATION),
                Map.entry(
                    ProcessInstanceMigrationAuditLogTransformer.class,
                    ValueType.PROCESS_INSTANCE_MIGRATION),
                Map.entry(
                    ProcessInstanceModificationAuditLogTransformer.class,
                    ValueType.PROCESS_INSTANCE_MODIFICATION),
                Map.entry(UserTaskAuditLogTransformer.class, ValueType.USER_TASK),
                Map.entry(VariableAddUpdateAuditLogTransformer.class, ValueType.VARIABLE))));
  }
}
