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
import static org.assertj.core.api.SoftAssertions.assertSoftly;
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
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerRegistry;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DefaultExporterResourceProviderTest {

  /**
   * Cache for reverse lookup: RecordValue class -> ValueType. Built once from {@link
   * ValueTypeMapping} to avoid repeated iteration.
   */
  private static final Map<Class<? extends RecordValue>, ValueType> RECORD_VALUE_TO_VALUE_TYPE =
      buildRecordValueToValueTypeMap();

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

  /**
   * Verifies that all `ExportHandlers` declare a `ValueType` in `getHandledValueType()` that
   * matches their `RecordValue` type parameter according to `ValueTypeMapping`.
   *
   * @see ValueTypeMapping for the correct mapping between `RecordValue` types and `ValueTypes`.
   */
  @Test
  void shouldHaveAllHandlersWithCorrectValueTypeMatchingRecordValueTypeParameter() {
    // given
    final var config = new ExporterConfiguration();
    // enable audit log and batch operation export to include their handlers
    config.getAuditLog().setEnabled(true);
    config.getBatchOperation().setExportItemsOnCreation(true);

    final var provider = new DefaultExporterResourceProvider();
    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new ExporterTestContext().setPartitionId(PROCESS_DEFINITION_PARTITION),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    // when
    final var handlers = provider.getExportHandlers();

    // then
    assertSoftly(
        softly -> {
          for (final var handler : handlers) {
            final var handlerName = handler.getClass().getSimpleName();
            final var recordValueType = retrieveRecordValueTypeParameterFrom(handler);

            softly
                .assertThat(recordValueType)
                .as(
                    """
                    Handler '%s' could not determine RecordValue type parameter.

                    Ensure the handler properly implements 'ExportHandler<T, R extends RecordValue>'
                    with a concrete RecordValue type.""",
                    handlerName)
                .isNotNull();

            if (recordValueType == null) {
              continue;
            }

            final var expectedValueType = getExpectedValueType(recordValueType);
            softly
                .assertThat(expectedValueType)
                .as(
                    """
                    Handler '%s' uses RecordValue type '%s' which has no mapping in ValueTypeMapping.

                    This could indicate:
                      - A missing mapping in ValueTypeMapping.java for this RecordValue type
                      - The handler is using an invalid RecordValue type

                    See ValueTypeMapping.java for valid RecordValue to ValueType mappings.""",
                    handlerName, recordValueType.getSimpleName())
                .isNotNull();

            if (expectedValueType == null) {
              continue;
            }

            final var actualValueType = handler.getHandledValueType();
            softly
                .assertThat(actualValueType)
                .as(
                    """
                    Handler '%s' has mismatched ValueType configuration:
                      - RecordValue type parameter: %s
                      - Declared getHandledValueType(): %s
                      - Expected ValueType: %s

                    Fix: Change '%s#getHandledValueType()' to return 'ValueType.%s'
                    See ValueTypeMapping.java for the correct mapping between RecordValue types and ValueTypes.""",
                    handlerName,
                    recordValueType.getSimpleName(),
                    actualValueType,
                    expectedValueType,
                    handlerName,
                    expectedValueType.name())
                .isEqualTo(expectedValueType);
          }
        });
  }

  /**
   * Retrieves the `RecordValue` type parameter `R` from an `ExportHandler<T, R extends
   * RecordValue>`.
   *
   * <p>Special handling for `AuditLogHandler` to extract from the contained transformer.
   */
  @SuppressWarnings("unchecked")
  private static Class<? extends RecordValue> retrieveRecordValueTypeParameterFrom(
      final ExportHandler<?, ?> handler) {
    // For AuditLogHandler, extract RecordValue from the transformer instance, because the handler
    // itself uses generic RecordValue
    if (handler instanceof AuditLogHandler<?> auditLogHandler) {
      return findRecordValueTypeParameterFromClass(auditLogHandler.getTransformer().getClass());
    }

    // For regular handlers, extract from class hierarchy
    return findRecordValueTypeParameterFromClass(handler.getClass());
  }

  /**
   * Finds the concrete {@link RecordValue} type parameter from a class hierarchy.
   *
   * <p>This method traverses the class hierarchy (interfaces and superclasses) to find a concrete
   * {@link RecordValue} subtype specified as a type argument. It handles several cases:
   *
   * <ul>
   *   <li><b>Direct implementation:</b> {@code class JobHandler implements ExportHandler<JobEntity,
   *       JobRecordValue>} -> returns {@code JobRecordValue.class}
   *   <li><b>Abstract class extension:</b> {@code class Handler extends
   *       AbstractHandler<IncidentRecordValue>} -> returns {@code IncidentRecordValue.class}
   *   <li><b>Deep hierarchy:</b> Recursively searches parent classes when the type argument is not
   *       directly visible
   * </ul>
   *
   * @param clazz the class to inspect for {@link RecordValue} type parameters
   * @return the concrete {@link RecordValue} subtype, or {@code null} if not found
   */
  @SuppressWarnings("unchecked")
  private static Class<? extends RecordValue> findRecordValueTypeParameterFromClass(
      final Class<?> clazz) {

    if (clazz == null || clazz.equals(Object.class)) {
      return null;
    }

    // Collect all parameterized types (interfaces + superclass)
    final Stream<ParameterizedType> parameterizedTypes =
        Stream.concat(
                Arrays.stream(clazz.getGenericInterfaces()),
                Stream.ofNullable(clazz.getGenericSuperclass()))
            .filter(ParameterizedType.class::isInstance)
            .map(ParameterizedType.class::cast);

    // Find first RecordValue type argument
    final var result =
        parameterizedTypes
            .flatMap(pt -> Arrays.stream(pt.getActualTypeArguments()))
            .filter(Class.class::isInstance)
            .map(Class.class::cast)
            .filter(RecordValue.class::isAssignableFrom)
            .filter(Class::isInterface)
            .filter(c -> !c.equals(RecordValue.class))
            .findFirst()
            .orElse(null);

    if (result != null) {
      return (Class<? extends RecordValue>) result;
    }

    // Recurse into superclass
    return findRecordValueTypeParameterFromClass(clazz.getSuperclass());
  }

  private static ValueType getExpectedValueType(
      final Class<? extends RecordValue> recordValueClass) {
    return RECORD_VALUE_TO_VALUE_TYPE.get(recordValueClass);
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
    // Build expected transformers map for partition 1 (all transformers)
    final Map<Class<?>, ValueType> allTransformers =
        AuditLogTransformerRegistry.createAllTransformers().stream()
            .collect(
                Collectors.toMap(
                    AuditLogTransformer::getClass,
                    transformer -> transformer.config().valueType()));

    // Build expected transformers map for other partitions (only all-partition transformers)
    final Map<Class<?>, ValueType> allPartitionTransformers =
        AuditLogTransformerRegistry.createAllPartitionTransformers().stream()
            .collect(
                Collectors.toMap(
                    AuditLogTransformer::getClass,
                    transformer -> transformer.config().valueType()));

    return Stream.of(
        Arguments.arguments(PROCESS_DEFINITION_PARTITION, allTransformers),
        Arguments.arguments(2, allPartitionTransformers));
  }

  private static Map<Class<? extends RecordValue>, ValueType> buildRecordValueToValueTypeMap() {
    return ValueTypeMapping.getAcceptedValueTypes().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                valueType -> ValueTypeMapping.get(valueType).getValueClass(), Function.identity()));
  }
}
