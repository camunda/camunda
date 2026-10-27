/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.AuthorizationScopeStateAdapter;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.tenant.PersistedTenant;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers only the authorization-cache invalidation side effect; end-to-end tenant deletion behavior
 * is covered by {@link TenantDeleteProcessorTest}.
 */
class TenantDeleteProcessorCacheInvalidationTest {

  private static final String TENANT_ID = "tenant-id";

  private AuthorizationScopeStateAdapter authorizationScopeStateAdapter;
  private MembershipStateAdapter membershipStateAdapter;
  private SideEffectWriter sideEffectWriter;
  private TenantDeleteProcessor processor;

  @BeforeEach
  void setUp() {
    final var tenantState = mock(TenantState.class);
    final var authorizationState = mock(AuthorizationState.class);
    final var userState = mock(UserState.class);
    final var membershipState = mock(MembershipState.class);

    final var persistedTenant = mock(PersistedTenant.class);
    when(persistedTenant.getTenantKey()).thenReturn(1L);
    when(persistedTenant.getTenantId()).thenReturn(TENANT_ID);
    when(persistedTenant.getName()).thenReturn("tenant-name");
    when(tenantState.getTenantById(TENANT_ID)).thenReturn(Optional.of(persistedTenant));
    when(authorizationState.getAuthorizationKeysForOwner(any(), any())).thenReturn(Set.of());

    final var state = mock(ProcessingState.class);
    when(state.getTenantState()).thenReturn(tenantState);
    when(state.getAuthorizationState()).thenReturn(authorizationState);
    when(state.getUserState()).thenReturn(userState);
    when(state.getMembershipState()).thenReturn(membershipState);

    final var stateWriter = mock(StateWriter.class);
    final var rejectionWriter = mock(TypedRejectionWriter.class);
    final var responseWriter = mock(TypedResponseWriter.class);
    sideEffectWriter = mock(SideEffectWriter.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);
    when(writers.response()).thenReturn(responseWriter);
    when(writers.sideEffect()).thenReturn(sideEffectWriter);

    authorizationScopeStateAdapter = mock(AuthorizationScopeStateAdapter.class);
    membershipStateAdapter = mock(MembershipStateAdapter.class);

    processor =
        new TenantDeleteProcessor(
            state,
            mock(PermissionsBehavior.class),
            mock(KeyGenerator.class),
            writers,
            mock(CommandDistributionBehavior.class),
            authorizationScopeStateAdapter,
            membershipStateAdapter);
  }

  @Test
  void shouldInvalidateBothAuthorizationCachesOnDistributedTenantDelete() {
    // given
    final var recordValue = new TenantRecord().setTenantId(TENANT_ID);
    final var command = new MockTypedRecord<>(1L, new RecordMetadata(), recordValue);

    // when
    processor.processDistributedCommand(command);

    // then
    final var sideEffectCaptor = ArgumentCaptor.forClass(SideEffectProducer.class);
    verify(sideEffectWriter).appendSideEffect(sideEffectCaptor.capture());
    sideEffectCaptor.getValue().flush();

    verify(authorizationScopeStateAdapter).invalidateAll();
    verify(membershipStateAdapter).invalidateAll();
  }
}
