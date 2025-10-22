/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.addProcessInstanceTags;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceTagIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final String TAG_VALUE_FOO = "foo_tag";
  public static final String TAG_VALUE_BAR = "bar_tag";

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByTag(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    addProcessInstanceTags(
        rdbmsWriter,
        processInstanceKey,
        List.of(
            ProcessInstanceFixtures.createRandomizedTag(
                b -> b.processInstanceKey(processInstanceKey).tagValue(TAG_VALUE_FOO))));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.tags(Set.of(TAG_VALUE_FOO)))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processDefinitionVersion()).isEqualTo(1);
    assertThat(instance.tags()).containsOnly(TAG_VALUE_FOO);
    assertThat(instance.hasIncident()).isFalse();
  }

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceWithMultipleTags(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processInstanceKeys = new ArrayList<Long>();

    for (int i = 0; i < 20; i++) {
      final Long processInstanceKey = nextKey();
      createAndSaveProcessInstance(
          rdbmsWriter,
          ProcessInstanceFixtures.createRandomized(
              b ->
                  b.processInstanceKey(processInstanceKey)
                      .processDefinitionId("test-process")
                      .processDefinitionKey(1337L)
                      .state(ProcessInstanceState.ACTIVE)
                      .startDate(NOW)
                      .parentProcessInstanceKey(-1L)
                      .parentElementInstanceKey(-1L)
                      .version(1)));
      processInstanceKeys.add(processInstanceKey);
    }

    processInstanceKeys.forEach(
        processInstanceKey -> {
          addProcessInstanceTags(
              rdbmsWriter,
              processInstanceKey,
              List.of(
                  ProcessInstanceFixtures.createRandomizedTag(
                      b -> b.processInstanceKey(processInstanceKey).tagValue(TAG_VALUE_FOO)),
                  ProcessInstanceFixtures.createRandomizedTag(
                      b -> b.processInstanceKey(processInstanceKey).tagValue(TAG_VALUE_BAR))));
        });

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.tags(Set.of(TAG_VALUE_FOO, TAG_VALUE_BAR)))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance.tags()).hasSize(2);
    assertThat(instance.tags()).containsExactlyInAnyOrder(TAG_VALUE_FOO, TAG_VALUE_BAR);
  }
}
