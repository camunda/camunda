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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
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
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests to ensure that all audit log transformers handle ValueTypes that are actually exported by
 * the CamundaExporter. This prevents a situation where a transformer is created for a ValueType
 * that the exporter doesn't export, which would result in no audit logs being generated.
 */
class AuditLogExporterFilterAlignmentTest {

  private final MockedStatic<ClientAdapter> mockedClientAdapterFactory =
      Mockito.mockStatic(ClientAdapter.class);

  private final ExporterConfiguration configuration = new ExporterConfiguration();
  private final ExporterTestContext testContext =
      new ExporterTestContext()
          .setConfiguration(new ExporterTestConfiguration<>("test", configuration));

  @BeforeEach
  void beforeEach() {
    mockedClientAdapterFactory
        .when(() -> ClientAdapter.of(configuration.getConnect()))
        .thenReturn(new StubClientAdapter());
    configuration.setCreateSchema(false);

    final CamundaExporter exporter = new CamundaExporter();
    exporter.configure(testContext);
  }

  @AfterEach
  void tearDown() {
    mockedClientAdapterFactory.close();
  }

  /**
   * Verifies that all audit log transformers handle ValueTypes that are accepted by the
   * CamundaExporter's record filter. If a transformer handles a ValueType that the exporter doesn't
   * export, the transformer will never be invoked.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("allAuditLogTransformers")
  void shouldExportValueTypeHandledByTransformer(final AuditLogTransformer<?> transformer) {
    final RecordFilter recordFilter = testContext.getRecordFilter();
    final TransformerConfig config = transformer.config();
    final ValueType valueType = config.valueType();

    assertThat(recordFilter.acceptValue(valueType))
        .as(
            "CamundaExporter should export ValueType %s which is handled by %s. "
                + "If this fails, add %s to CamundaExporterRecordFilter.VALUE_TYPES_2_EXPORT",
            valueType, transformer.getClass().getSimpleName(), valueType)
        .isTrue();
  }

  /**
   * Verifies that the CamundaExporter accepts the record types (EVENT and COMMAND_REJECTION) that
   * are used by audit log transformers.
   */
  @Test
  void shouldAcceptEventRecordType() {
    final RecordFilter recordFilter = testContext.getRecordFilter();

    assertThat(recordFilter.acceptType(RecordType.EVENT))
        .as("CamundaExporter should accept EVENT record type for audit logging")
        .isTrue();
  }

  @Test
  void shouldAcceptCommandRejectionRecordType() {
    final RecordFilter recordFilter = testContext.getRecordFilter();

    assertThat(recordFilter.acceptType(RecordType.COMMAND_REJECTION))
        .as("CamundaExporter should accept COMMAND_REJECTION record type for audit logging")
        .isTrue();
  }

  private static Stream<Arguments> allAuditLogTransformers() {
    return getAllTransformers().stream()
        .map(
            transformer ->
                Arguments.of(Named.of(transformer.getClass().getSimpleName(), transformer)));
  }

  private static List<AuditLogTransformer<?>> getAllTransformers() {
    final List<AuditLogTransformer<?>> transformers = new ArrayList<>();
    transformers.add(new AuthorizationAuditLogTransformer());
    transformers.add(new BatchOperationCreationAuditLogTransformer());
    transformers.add(new BatchOperationLifecycleManagementAuditLogTransformer());
    transformers.add(new DecisionAuditLogTransformer());
    transformers.add(new DecisionEvaluationAuditLogTransformer());
    transformers.add(new DecisionRequirementsRecordAuditLogTransformer());
    transformers.add(new FormAuditLogTransformer());
    transformers.add(new GroupAuditLogTransformer());
    transformers.add(new GroupEntityAuditLogTransformer());
    transformers.add(new IncidentResolutionAuditLogTransformer());
    transformers.add(new MappingRuleAuditLogTransformer());
    transformers.add(new ProcessAuditLogTransformer());
    transformers.add(new ProcessInstanceCancelAuditLogTransformer());
    transformers.add(new ProcessInstanceCreationAuditLogTransformer());
    transformers.add(new ProcessInstanceMigrationAuditLogTransformer());
    transformers.add(new ProcessInstanceModificationAuditLogTransformer());
    transformers.add(new ResourceAuditLogTransformer());
    transformers.add(new RoleAuditLogTransformer());
    transformers.add(new RoleEntityAuditLogTransformer());
    transformers.add(new TenantAuditLogTransformer());
    transformers.add(new TenantEntityAuditLogTransformer());
    transformers.add(new UserAuditLogTransformer());
    transformers.add(new UserTaskAuditLogTransformer());
    transformers.add(new VariableAddUpdateAuditLogTransformer());
    return transformers;
  }

  private static final class StubClientAdapter implements ClientAdapter {
    private final SearchEngineClient searchEngineClient = mock(SearchEngineClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ObjectMapper objectMapper() {
      return objectMapper;
    }

    @Override
    public SearchEngineClient getSearchEngineClient() {
      return searchEngineClient;
    }

    @Override
    public BatchRequest createBatchRequest() {
      return mock(BatchRequest.class);
    }

    @Override
    public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
      return new NoopExporterEntityCacheProvider();
    }

    @Override
    public void close() {}
  }

  private static final class NoopExporterEntityCacheProvider
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<String, CachedBatchOperationEntity> getBatchOperationCacheLoader(
        final String batchOperationIndexName) {
      return k -> null;
    }

    @Override
    public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
        final String processIndexName) {
      return k -> null;
    }

    @Override
    public CacheLoader<Long, CachedDecisionRequirementsEntity> getDecisionRequirementsCacheLoader(
        final String decisionIndexName) {
      return k -> null;
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return k -> null;
    }
  }
}
