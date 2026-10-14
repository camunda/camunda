/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbMappingRuleStateVersionTest {

  private MutableProcessingState processingState;
  private MutableMappingRuleState mappingRuleState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    mappingRuleState = processingState.getMappingRuleState();
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldReadRuleOfTheCurrentVersionByIdAndByClaim() {
    // given
    mappingRuleState.create(rule("m", "c1", "v1", 0));

    // then
    assertThat(mappingRuleState.get("m")).isPresent();
    assertThat(mappingRuleState.get("c1", "v1")).isPresent();
  }

  @Test
  void shouldMoveRuleToAnotherClaimAtItsVersion() {
    // given
    mappingRuleState.create(rule("m", "c1", "v1", 0));

    // when: the rule moves to another claim in a pending version
    mappingRuleState.update(rule("m", "c2", "v2", 2));

    // then: until the switch, the old claim is still the visible one
    assertThat(mappingRuleState.get("m").orElseThrow().getClaimName()).isEqualTo("c1");
    assertThat(mappingRuleState.get("c1", "v1")).isPresent();

    // when
    policyUpdateState.setCurrentVersion(2);

    // then: the old claim is released and the new claim holds the rule
    assertThat(mappingRuleState.get("c1", "v1")).isEmpty();
    assertThat(mappingRuleState.get("c2", "v2")).isPresent();
    assertThat(mappingRuleState.get("m").orElseThrow().getClaimName()).isEqualTo("c2");
  }

  @Test
  void shouldHideRuleDeletedAtCurrentVersion() {
    // given
    mappingRuleState.create(rule("m", "c1", "v1", 0));

    // when
    mappingRuleState.delete(rule("m", "c1", "v1", 1));
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(mappingRuleState.get("m")).isEmpty();
    assertThat(mappingRuleState.get("c1", "v1")).isEmpty();
  }

  @Test
  void shouldLetPolicyVersionReadItsOwnPendingRule() {
    // given
    mappingRuleState.create(rule("m", "c1", "v1", 3));

    // then
    assertThat(mappingRuleState.get("m", 3)).isPresent();
    assertThat(mappingRuleState.get("c1", "v1", 3)).isPresent();
    assertThat(mappingRuleState.get("m")).isEmpty();
    assertThat(mappingRuleState.get("c1", "v1")).isEmpty();
  }

  private static MappingRuleRecord rule(
      final String id, final String claimName, final String claimValue, final long version) {
    return new MappingRuleRecord()
        .setMappingRuleId(id)
        .setName("name")
        .setClaimName(claimName)
        .setClaimValue(claimValue)
        .setPolicyVersion(version);
  }
}
