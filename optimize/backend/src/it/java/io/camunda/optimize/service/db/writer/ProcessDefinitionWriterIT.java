/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionWriterIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final RandomStringGenerator KEY_GENERATOR =
      new RandomStringGenerator.Builder().withinRange('a', 'z').get();

  private ProcessDefinitionWriter processDefinitionWriter;

  @BeforeEach
  void setup() {
    processDefinitionWriter = embeddedOptimizeExtension.getBean(ProcessDefinitionWriter.class);
  }

  @Test
  void shouldOnlyMarkNotYetOnboardedAndNotDeletedVersionsOfSelectedKeysAsOnboarded() {
    // given
    final String selectedKey = KEY_GENERATOR.generate(8);
    final String otherKey = KEY_GENERATOR.generate(8);
    final ProcessDefinitionOptimizeDto alreadyOnboarded = definition(selectedKey, "1", true, false);
    final ProcessDefinitionOptimizeDto notOnboarded = definition(selectedKey, "2", false, false);
    final ProcessDefinitionOptimizeDto deleted = definition(selectedKey, "3", false, true);
    final ProcessDefinitionOptimizeDto otherKeyNotOnboarded =
        definition(otherKey, "1", false, false);
    persistProcessDefinitions(
        List.of(alreadyOnboarded, notOnboarded, deleted, otherKeyNotOnboarded));

    // when
    processDefinitionWriter.markDefinitionKeysAsOnboarded(Set.of(selectedKey));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final Map<String, ProcessDefinitionOptimizeDto> byId =
        databaseIntegrationTestExtension.getAllProcessDefinitions().stream()
            .collect(Collectors.toMap(ProcessDefinitionOptimizeDto::getId, d -> d));
    assertThat(byId.get(alreadyOnboarded.getId()).isOnboarded()).isTrue();
    assertThat(byId.get(notOnboarded.getId()).isOnboarded()).isTrue();
    assertThat(byId.get(deleted.getId()).isOnboarded()).isFalse();
    assertThat(byId.get(otherKeyNotOnboarded.getId()).isOnboarded()).isFalse();
  }

  private static ProcessDefinitionOptimizeDto definition(
      final String key, final String version, final boolean onboarded, final boolean deleted) {
    return ProcessDefinitionOptimizeDto.builder()
        .id(key + "-" + version)
        .key(key)
        .version(version)
        .name(key)
        .tenantId(ZEEBE_DEFAULT_TENANT_ID)
        .bpmn20Xml("<definitions/>")
        .onboarded(onboarded)
        .deleted(deleted)
        .build();
  }
}
