/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsReader;
import io.camunda.search.clients.reader.ProcessInstanceReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.exception.TenantAccessDeniedException;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.core.auth.SecurityContext;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.ResourceAccessController;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CamundaSearchClientsTest {

  private final SearchClientReaders readers = mock(SearchClientReaders.class);
  private final ResourceAccessController resourceAccessController =
      mock(ResourceAccessController.class);
  private final ProcessInstanceReader processInstanceReader = mock(ProcessInstanceReader.class);
  private final ProcessDefinitionInstanceStatisticsReader
      processDefinitionInstanceStatisticsReader =
          mock(ProcessDefinitionInstanceStatisticsReader.class);

  private CamundaSearchClients camundaSearchClients;

  @BeforeEach
  void setUp() {
    camundaSearchClients =
        (CamundaSearchClients)
            new CamundaSearchClients(
                    Map.of("default", readers), Map.of("default", resourceAccessController))
                .withPhysicalTenant("default");

    // Make the resource access controller simply invoke the applier with a no-op check
    when(resourceAccessController.doSearch(any(), any()))
        .thenAnswer(
            invocation -> {
              final Function<ResourceAccessChecks, Object> applier = invocation.getArgument(1);
              return applier.apply(ResourceAccessChecks.disabled());
            });
  }

  @Nested
  class DoSearchWithEntityReaderExceptional {

    @Test
    void shouldRethrowCamundaSearchException() {
      // given
      final var originalException = new CamundaSearchException("already wrapped", Reason.NOT_FOUND);
      when(readers.processInstanceReader()).thenReturn(processInstanceReader);
      when(processInstanceReader.search(any(), any())).thenThrow(originalException);

      // when / then
      assertThatThrownBy(
              () -> camundaSearchClients.searchProcessInstances(ProcessInstanceQuery.of(b -> b)))
          .isSameAs(originalException);
    }

    @Test
    void shouldWrapUnexpectedExceptionIntoCamundaSearchException() {
      // given
      final var cause = new RuntimeException("something went wrong in the search layer");
      when(readers.processInstanceReader()).thenReturn(processInstanceReader);
      when(processInstanceReader.search(any(), any())).thenThrow(cause);

      // when / then
      assertThatThrownBy(
              () -> camundaSearchClients.searchProcessInstances(ProcessInstanceQuery.of(b -> b)))
          .isInstanceOf(CamundaSearchException.class)
          .hasCause(cause)
          .extracting(t -> ((CamundaSearchException) t).getReason())
          .isEqualTo(Reason.SEARCH_SERVER_FAILED);
    }
  }

  @Nested
  class DoSearchWithStatisticsReaderExceptional {

    @Test
    void shouldRethrowCamundaSearchException() {
      // given
      final var originalException = new CamundaSearchException("already wrapped", Reason.NOT_FOUND);
      when(readers.processDefinitionInstanceStatisticsReader())
          .thenReturn(processDefinitionInstanceStatisticsReader);
      when(processDefinitionInstanceStatisticsReader.aggregate(any(), any()))
          .thenThrow(originalException);

      // when / then
      assertThatThrownBy(
              () ->
                  camundaSearchClients.processDefinitionInstanceStatistics(
                      ProcessDefinitionInstanceStatisticsQuery.of(b -> b)))
          .isSameAs(originalException);
    }

    @Test
    void shouldWrapUnexpectedExceptionIntoCamundaSearchException() {
      // given
      final var cause = new RuntimeException("something went wrong in the statistics layer");
      when(readers.processDefinitionInstanceStatisticsReader())
          .thenReturn(processDefinitionInstanceStatisticsReader);
      when(processDefinitionInstanceStatisticsReader.aggregate(any(), any())).thenThrow(cause);

      // when / then
      assertThatThrownBy(
              () ->
                  camundaSearchClients.processDefinitionInstanceStatistics(
                      ProcessDefinitionInstanceStatisticsQuery.of(b -> b)))
          .isInstanceOf(CamundaSearchException.class)
          .hasCause(cause)
          .extracting(t -> ((CamundaSearchException) t).getReason())
          .isEqualTo(Reason.SEARCH_SERVER_FAILED);
    }
  }

  @Nested
  class DoGetExceptional {

    @Test
    void shouldReturnEmptyWhenTenantAccessIsDenied() {
      // given
      when(readers.processInstanceReader()).thenReturn(processInstanceReader);
      when(resourceAccessController.doGet(any(), any()))
          .thenThrow(new TenantAccessDeniedException("tenant access denied"));

      // when / then — getProcessInstance wraps the Optional and re-throws NOT_FOUND when empty
      assertThatThrownBy(() -> camundaSearchClients.getProcessInstance(1L))
          .isInstanceOf(CamundaSearchException.class)
          .extracting(t -> ((CamundaSearchException) t).getReason())
          .isEqualTo(Reason.NOT_FOUND);
    }

    @Test
    void shouldRethrowCamundaSearchException() {
      // given
      final var originalException = new CamundaSearchException("already wrapped", Reason.NOT_FOUND);
      when(readers.processInstanceReader()).thenReturn(processInstanceReader);
      when(resourceAccessController.doGet(any(), any()))
          .thenAnswer(
              invocation -> {
                final Function<ResourceAccessChecks, Object> applier = invocation.getArgument(1);
                return applier.apply(ResourceAccessChecks.disabled());
              });
      when(processInstanceReader.getByKey(any(long.class), any())).thenThrow(originalException);

      // when / then
      assertThatThrownBy(() -> camundaSearchClients.getProcessInstance(1L))
          .isSameAs(originalException);
    }

    @Test
    void shouldWrapUnexpectedExceptionIntoCamundaSearchException() {
      // given
      final var cause = new RuntimeException("something went wrong in the search layer");
      when(readers.processInstanceReader()).thenReturn(processInstanceReader);
      when(resourceAccessController.doGet(any(), any()))
          .thenAnswer(
              invocation -> {
                final Function<ResourceAccessChecks, Object> applier = invocation.getArgument(1);
                return applier.apply(ResourceAccessChecks.disabled());
              });
      when(processInstanceReader.getByKey(any(long.class), any())).thenThrow(cause);

      // when / then
      assertThatThrownBy(() -> camundaSearchClients.getProcessInstance(1L))
          .isInstanceOf(CamundaSearchException.class)
          .hasCause(cause)
          .extracting(t -> ((CamundaSearchException) t).getReason())
          .isEqualTo(Reason.SEARCH_SERVER_FAILED);
    }
  }

  @Nested
  class WithPhysicalTenant {

    private static final String DEFAULT = "default";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private final SearchClientReaders tenantAReaders = mock(SearchClientReaders.class);
    private final SearchClientReaders tenantBReaders = mock(SearchClientReaders.class);
    private final ProcessInstanceReader tenantAProcessInstanceReader =
        mock(ProcessInstanceReader.class);
    private final ProcessInstanceReader tenantBProcessInstanceReader =
        mock(ProcessInstanceReader.class);

    @Test
    void shouldThrowWhenNoTenantsAreConfigured() {
      // given

      // when / then
      assertThatThrownBy(() -> camundaSearchClients.withPhysicalTenant(TENANT_A))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown physical tenant: '" + TENANT_A + "'");
    }

    @Test
    void shouldThrowWhenPhysicalTenantIsUnknown() {
      // given
      final var clients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers, TENANT_A, tenantAReaders, TENANT_B, tenantBReaders),
              Map.of(
                  DEFAULT, resourceAccessController,
                  TENANT_A, resourceAccessController,
                  TENANT_B, resourceAccessController));

      // when / then
      assertThatThrownBy(() -> clients.withPhysicalTenant("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown physical tenant: 'unknown'")
          .hasMessageContaining(TENANT_A)
          .hasMessageContaining(TENANT_B);
    }

    @Test
    void shouldRouteReadsToTheSelectedTenantsReaders() {
      // given
      when(tenantAReaders.processInstanceReader()).thenReturn(tenantAProcessInstanceReader);
      when(tenantBReaders.processInstanceReader()).thenReturn(tenantBProcessInstanceReader);

      final var clients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers, TENANT_A, tenantAReaders, TENANT_B, tenantBReaders),
              Map.of(
                  DEFAULT, resourceAccessController,
                  TENANT_A, resourceAccessController,
                  TENANT_B, resourceAccessController));

      // when
      clients.withPhysicalTenant(TENANT_A).searchProcessInstances(ProcessInstanceQuery.of(b -> b));

      // then
      verify(tenantAProcessInstanceReader).search(any(), any());
      verifyNoInteractions(tenantBProcessInstanceReader);
    }

    @Test
    void shouldThrowWhenReadIsAttemptedWithoutScopingToAPhysicalTenant() {
      // given — an unscoped base instance (withPhysicalTenant not yet called)
      final var unscopedClients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers), Map.of(DEFAULT, resourceAccessController));

      // when / then
      assertThatThrownBy(
              () -> unscopedClients.searchProcessInstances(ProcessInstanceQuery.of(b -> b)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("withPhysicalTenant");
    }

    @Test
    void shouldThrowWhenStatisticsReadIsAttemptedWithoutScoping() {
      // given — an unscoped base instance
      final var unscopedClients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers), Map.of(DEFAULT, resourceAccessController));

      // when / then — a ResourceAccessController-backed (non reader-first) path must also fail fast
      assertThatThrownBy(() -> unscopedClients.processInstanceFlowNodeStatistics(1L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("withPhysicalTenant");
    }

    @Test
    void shouldThrowWhenGetViaResourceAccessControllerIsAttemptedWithoutScoping() {
      // given — an unscoped base instance
      final var unscopedClients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers), Map.of(DEFAULT, resourceAccessController));

      // when / then — a doGet(...)-backed path must fail fast with ISE, not a wrapped
      // CamundaSearchException
      assertThatThrownBy(() -> unscopedClients.getDeployedResource(1L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("withPhysicalTenant");
    }

    @Test
    void shouldPreserveSecurityContextAndResourceAccessControllerOnTheReturnedInstance() {
      // given
      when(tenantAReaders.processInstanceReader()).thenReturn(tenantAProcessInstanceReader);
      when(readers.processInstanceReader()).thenReturn(processInstanceReader);
      final var securityContext = SecurityContext.of(b -> b);
      final var clients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers, TENANT_A, tenantAReaders),
              Map.of(DEFAULT, resourceAccessController, TENANT_A, resourceAccessController));

      // when
      clients
          .withPhysicalTenant(TENANT_A)
          .withSecurityContext(securityContext)
          .searchProcessInstances(ProcessInstanceQuery.of(b -> b));

      // then — switching the security context must not reset the physical tenant scoping
      verify(tenantAProcessInstanceReader).search(any(), any());
      verifyNoInteractions(processInstanceReader);
    }
  }

  @Nested
  class WithPhysicalTenantSelectsPerTenantRac {

    private static final String PT_A = "pt-a";
    private static final String PT_B = "pt-b";

    @Test
    void shouldRouteReadToCorrectRac() {
      // given — two PTs with distinct RAC mocks (plus the required "default" entry)
      final ResourceAccessController racDefault = mock(ResourceAccessController.class);
      final ResourceAccessController racA = mock(ResourceAccessController.class);
      final ResourceAccessController racB = mock(ResourceAccessController.class);
      final SearchClientReaders readersDefault = mock(SearchClientReaders.class);
      final SearchClientReaders readersA = mock(SearchClientReaders.class);
      final SearchClientReaders readersB = mock(SearchClientReaders.class);
      final ProcessInstanceReader processInstanceReaderA = mock(ProcessInstanceReader.class);
      final ProcessInstanceReader processInstanceReaderB = mock(ProcessInstanceReader.class);

      // make each RAC call through its applier
      when(racA.doSearch(any(), any()))
          .thenAnswer(
              inv -> {
                final Function<ResourceAccessChecks, Object> applier = inv.getArgument(1);
                return applier.apply(ResourceAccessChecks.disabled());
              });
      when(racB.doSearch(any(), any()))
          .thenAnswer(
              inv -> {
                final Function<ResourceAccessChecks, Object> applier = inv.getArgument(1);
                return applier.apply(ResourceAccessChecks.disabled());
              });

      when(readersA.processInstanceReader()).thenReturn(processInstanceReaderA);
      when(readersB.processInstanceReader()).thenReturn(processInstanceReaderB);

      final CamundaSearchClients clients =
          new CamundaSearchClients(
              Map.of("default", readersDefault, PT_A, readersA, PT_B, readersB),
              Map.of("default", racDefault, PT_A, racA, PT_B, racB));

      // when scoped to PT_A
      clients.withPhysicalTenant(PT_A).searchProcessInstances(ProcessInstanceQuery.of(b -> b));

      // then only racA is called
      verify(racA).doSearch(any(), any());
      verifyNoInteractions(racB);

      // when scoped to PT_B
      clearInvocations(racA, racB);
      clients.withPhysicalTenant(PT_B).searchProcessInstances(ProcessInstanceQuery.of(b -> b));

      // then only racB is called
      verify(racB).doSearch(any(), any());
      verifyNoInteractions(racA);
    }
  }
}
