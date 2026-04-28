/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
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
        new CamundaSearchClients(
            Map.of("default", readers), resourceAccessController, SecurityContext.of(b -> b));

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
              resourceAccessController,
              SecurityContext.of(b -> b));

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
              resourceAccessController,
              SecurityContext.of(b -> b));

      // when
      clients.withPhysicalTenant(TENANT_A).searchProcessInstances(ProcessInstanceQuery.of(b -> b));

      // then
      verify(tenantAProcessInstanceReader).search(any(), any());
      verifyNoInteractions(tenantBProcessInstanceReader);
    }

    @Test
    void shouldPreserveSecurityContextAndResourceAccessControllerOnTheReturnedInstance() {
      // given
      final var securityContext = SecurityContext.of(b -> b);
      final var clients =
          new CamundaSearchClients(
              Map.of(DEFAULT, readers, TENANT_A, tenantAReaders),
              resourceAccessController,
              securityContext);

      // when
      final var tenantScoped = clients.withPhysicalTenant(TENANT_A);

      // then
      assertThat(tenantScoped.withSecurityContext(securityContext)).isNotNull();
    }
  }
}
