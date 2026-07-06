/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionReaderIT extends AbstractBrokerlessZeebeCCSMIT {
  private static final RandomStringGenerator KEY_GENERATOR =
      new RandomStringGenerator.Builder().withinRange('a', 'z').get();
  private ProcessDefinitionReader processDefinitionReader;

  @BeforeEach
  void setup() {
    processDefinitionReader = embeddedOptimizeExtension.getBean(ProcessDefinitionReader.class);
  }

  @Test
  void shouldFindNoProcessDefinitions() {
    final var it = processDefinitionReader.getAllProcessDefinitions();
    assertThat(it).isExhausted();
  }

  @Test
  void shouldFindProcessDefinition() {
    final var key = KEY_GENERATOR.generate(8);
    final List<ProcessDefinitionOptimizeDto> definitions =
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(key)
                .key(key)
                .version("1")
                .name(key)
                .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                .bpmn20Xml("<definitions/>")
                .build());
    persistProcessDefinitions(definitions);

    final var it = processDefinitionReader.getAllProcessDefinitions();
    assertThat(it).hasNext();

    final var def = it.next();
    assertThat(def.getKey()).isEqualTo(key);
    assertThat(def.getVersion()).isEqualTo("1");
    assertThat(def.getName()).isEqualTo(key);
    assertThat(def.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(def.getType()).isEqualTo(DefinitionType.PROCESS);
    assertThat(def.getBpmn20Xml()).isNull();

    assertThat(it).isExhausted();
  }

  @Test
  void shouldFindLargeNumberOfProcessDefinitions() {
    final var prefix = KEY_GENERATOR.generate(8);
    final List<ProcessDefinitionOptimizeDto> definitions =
        IntStream.range(1, 2000)
            .mapToObj(i -> prefix + "-" + i)
            .map(
                key ->
                    ProcessDefinitionOptimizeDto.builder()
                        .id(key)
                        .key(key)
                        .version("1")
                        .name(key)
                        .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                        .bpmn20Xml("<definitions/>")
                        .build())
            .toList();
    persistProcessDefinitions(definitions);

    final var it = processDefinitionReader.getAllProcessDefinitions();
    assertThat(it).hasNext();

    final var keys = new ArrayList<>();
    while (it.hasNext()) {
      final var def = it.next();
      keys.add(def.getKey());
    }
    assertThat(it).isExhausted();

    final var expectedKeys =
        definitions.stream().map(ProcessDefinitionOptimizeDto::getKey).toList();
    assertThat(keys).containsExactlyInAnyOrderElementsOf(expectedKeys);
  }
}
