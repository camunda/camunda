/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.search.schema.SchemaManagerContainer;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractDataGeneratorTest {

  private final ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
  private final CamundaAuthenticationProvider authenticationProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaClient client = mock(CamundaClient.class);
  private final ResourceServices resourceServices = mock(ResourceServices.class);
  private final ProcessInstanceServices processInstanceServices =
      mock(ProcessInstanceServices.class);
  private final PhysicalTenantIds physicalTenantIds = mock(PhysicalTenantIds.class);
  private final SchemaManagerContainer schemaManagerContainer = mock(SchemaManagerContainer.class);

  private final CamundaAuthentication anonymousAuthentication = CamundaAuthentication.anonymous();

  private TestDataGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new TestDataGenerator();
    generator.serviceRegistry = serviceRegistry;
    generator.authenticationProvider = authenticationProvider;
    generator.client = client;
    generator.physicalTenantIds = physicalTenantIds;
    generator.schemaManagerContainer = schemaManagerContainer;

    when(authenticationProvider.getAnonymousCamundaAuthentication())
        .thenReturn(anonymousAuthentication);
    when(serviceRegistry.resourceServices(anyString())).thenReturn(resourceServices);
    when(serviceRegistry.processInstanceServices(anyString())).thenReturn(processInstanceServices);
    when(physicalTenantIds.known())
        .thenReturn(Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));
  }

  @Test
  void shouldDeployProcessAnonymouslyWithoutTouchingCredentialFreeClient() {
    // given
    final ProcessMetadataValue metadata = mock(ProcessMetadataValue.class);
    when(metadata.getProcessDefinitionKey()).thenReturn(123L);
    final DeploymentRecord deploymentRecord = mock(DeploymentRecord.class);
    when(deploymentRecord.getProcessesMetadata()).thenReturn(List.of(metadata));
    when(resourceServices.deployResources(
            any(DeployResourcesRequest.class), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.completedFuture(deploymentRecord));

    // when
    final Long processDefinitionKey =
        generator.deployProcess(false, "tenant1", "usertest/single-task.bpmn");

    // then
    assertThat(processDefinitionKey).isEqualTo(123L);
    verify(resourceServices)
        .deployResources(any(DeployResourcesRequest.class), eq(anonymousAuthentication));
    verifyNoInteractions(client);
  }

  @Test
  void shouldStartProcessInstanceAnonymouslyWithoutTouchingCredentialFreeClient() {
    // given
    final ProcessInstanceCreationRecord record = mock(ProcessInstanceCreationRecord.class);
    when(record.getProcessInstanceKey()).thenReturn(456L);
    when(processInstanceServices.createProcessInstance(
            any(ProcessInstanceCreateRequest.class), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when
    final long processInstanceKey =
        generator.startProcessInstance(false, "tenant1", "orderProcess", null);

    // then
    assertThat(processInstanceKey).isEqualTo(456L);
    verify(processInstanceServices)
        .createProcessInstance(
            any(ProcessInstanceCreateRequest.class), eq(anonymousAuthentication));
    verifyNoInteractions(client);
  }

  @Test
  void shouldCancelProcessInstanceAnonymouslyWithoutTouchingCredentialFreeClient() {
    // given
    when(processInstanceServices.cancelProcessInstance(
            any(ProcessInstanceCancelRequest.class), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    generator.cancelProcessInstance(false, 789L);

    // then
    verify(processInstanceServices)
        .cancelProcessInstance(
            any(ProcessInstanceCancelRequest.class), eq(anonymousAuthentication));
    verifyNoInteractions(client);
  }

  @Test
  void shouldSwallowDeployFailureWhenIgnoreExceptionIsTrue() {
    // given
    when(resourceServices.deployResources(
            any(DeployResourcesRequest.class), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("401")));

    // when
    final Long processDefinitionKey = generator.deployProcess(true, "tenant1", "missing.bpmn");

    // then
    assertThat(processDefinitionKey).isNull();
  }

  @Test
  void shouldRetryOnceThenReturnKeyWhenFirstStartProcessInstanceAttemptFails() {
    // given
    final ProcessInstanceCreationRecord record = mock(ProcessInstanceCreationRecord.class);
    when(record.getProcessInstanceKey()).thenReturn(456L);
    when(processInstanceServices.createProcessInstance(
            any(ProcessInstanceCreateRequest.class), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("401")))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when
    final long processInstanceKey =
        generator.startProcessInstance(false, "tenant1", "orderProcess", null);

    // then
    assertThat(processInstanceKey).isEqualTo(456L);
    verify(processInstanceServices, times(2))
        .createProcessInstance(
            any(ProcessInstanceCreateRequest.class), eq(anonymousAuthentication));
  }

  @Test
  void shouldReturnZeroWhenBothStartProcessInstanceAttemptsFailAndIgnoreExceptionIsTrue() {
    // given
    when(processInstanceServices.createProcessInstance(
            any(ProcessInstanceCreateRequest.class), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("401")));

    // when
    final long processInstanceKey =
        generator.startProcessInstance(true, "tenant1", "orderProcess", null);

    // then
    assertThat(processInstanceKey).isZero();
  }

  @Test
  void shouldResolveIncidentForProcessInstanceAnonymouslyWithoutTouchingCredentialFreeClient() {
    // given
    final BatchOperationCreationRecord record = mock(BatchOperationCreationRecord.class);
    when(processInstanceServices.resolveProcessInstanceIncidents(
            eq(789L), eq(anonymousAuthentication)))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when
    generator.resolveIncidentForProcessInstance(789L);

    // then
    verify(processInstanceServices)
        .resolveProcessInstanceIncidents(eq(789L), eq(anonymousAuthentication));
    verifyNoInteractions(client);
  }

  @Test
  void shouldThrowWhenVariablesPayloadIsInvalidJson() {
    // when / then
    assertThatThrownBy(
            () -> generator.startProcessInstance(false, "tenant1", "orderProcess", "{invalid"))
        .isInstanceOf(OperateRuntimeException.class);
    verifyNoInteractions(processInstanceServices);
  }

  @Test
  void shouldReportSchemaReadyOnlyWhenAllKnownPhysicalTenantsAreInitialized() {
    // given
    when(physicalTenantIds.known()).thenReturn(Set.of("tenant1", "tenant2"));
    when(schemaManagerContainer.isInitialized(anyString())).thenReturn(true);

    // when / then
    assertThat(generator.isSchemaReady()).isTrue();

    // given
    when(schemaManagerContainer.isInitialized("tenant2")).thenReturn(false);

    // when / then
    assertThat(generator.isSchemaReady()).isFalse();
  }

  @Test
  void shouldReportSchemaReadyWhenSchemaManagerContainerIsAbsent() {
    // given: RDBMS storage, which has no async schema-init phase and never registers the bean
    generator.schemaManagerContainer = null;

    // when / then
    assertThat(generator.isSchemaReady()).isTrue();
  }

  private static final class TestDataGenerator extends AbstractDataGenerator {

    @Override
    public boolean createZeebeData(final boolean manuallyCalled) {
      return true;
    }
  }
}
