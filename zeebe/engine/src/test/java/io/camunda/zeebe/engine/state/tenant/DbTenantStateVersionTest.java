/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbTenantStateVersionTest {

  private MutableProcessingState processingState;
  private MutableTenantState tenantState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    tenantState = processingState.getTenantState();
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldReadTenantOfTheCurrentVersionOnly() {
    // given
    tenantState.createTenant(tenant("t", "A", 0));
    tenantState.delete(tenant("t", "A", 1));

    // then: the tombstone of version 1 is pending
    assertThat(tenantState.getTenantById("t")).isPresent();
    assertThat(tenantState.getTenantById("t", 1)).isEmpty();

    // when
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(tenantState.getTenantById("t")).isEmpty();
  }

  @Test
  void shouldVisitEveryVisibleTenantOnce() {
    // given
    tenantState.createTenant(tenant("a", "A", 0));
    tenantState.createTenant(tenant("b", "B", 0));
    tenantState.createTenant(tenant("c", "C", 0));
    tenantState.delete(tenant("b", "B", 0));
    tenantState.updateTenant(tenant("c", "C2", 2));
    policyUpdateState.setCurrentVersion(1);

    // when
    final var visited = new ArrayList<String>();
    tenantState.forEachTenant(
        id -> {
          visited.add(id);
          return true;
        });

    // then
    assertThat(visited).containsExactlyInAnyOrder("a", "c");
  }

  private static TenantRecord tenant(final String id, final String name, final long version) {
    return new TenantRecord().setTenantId(id).setName(name).setPolicyVersion(version);
  }
}
