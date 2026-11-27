/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.access.MappingRuleDto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MappingRuleRepositoryIT extends AbstractBrokerlessZeebeCCSMIT {

  private MappingRuleRepository mappingRuleRepository;

  @BeforeEach
  void setup() {
    mappingRuleRepository = embeddedOptimizeExtension.getBean(MappingRuleRepository.class);
  }

  @Test
  void shouldReplaceMappingRuleOnUpsert() {
    // given
    final String mappingRuleId = uniqueId();
    mappingRuleRepository.upsert(new MappingRuleDto(mappingRuleId, "groups", "analysts", "A"));
    final MappingRuleDto changed =
        new MappingRuleDto(mappingRuleId, "$.department", "finance", "Finance department");

    // when
    mappingRuleRepository.upsert(changed);

    // then
    assertThat(mappingRuleRepository.get(mappingRuleId)).contains(changed);
    assertThat(mappingRuleRepository.getAll()).containsExactly(changed);
  }

  @Test
  void shouldDeleteOnlyTheGivenMappingRule() {
    // given
    final MappingRuleDto deleted = new MappingRuleDto(uniqueId(), "groups", "analysts", "A");
    final MappingRuleDto kept = new MappingRuleDto(uniqueId(), "groups", "admins", "B");
    mappingRuleRepository.upsert(deleted);
    mappingRuleRepository.upsert(kept);

    // when
    mappingRuleRepository.delete(deleted.mappingRuleId());
    mappingRuleRepository.delete(uniqueId());

    // then
    assertThat(mappingRuleRepository.get(deleted.mappingRuleId())).isEmpty();
    assertThat(mappingRuleRepository.getAll()).containsExactly(kept);
  }

  private static String uniqueId() {
    return "mapping-rule-" + UUID.randomUUID();
  }
}
