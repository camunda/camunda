/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceTagIT {

  public static final int PARTITION_ID = 0;
  public static final String TAG_VALUE_FOO = "foo_tag";
  public static final String TAG_VALUE_BAR = "bar_tag";

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByTag(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processDefinitionId = "test-process-" + nextKey();
    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId(processDefinitionId)
                    .state(ProcessInstanceState.ACTIVE)
                    .tags(Set.of(TAG_VALUE_FOO))));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(
                        f ->
                            f.processDefinitionIds(processDefinitionId)
                                .tags(Set.of(TAG_VALUE_FOO)))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items().getFirst().tags()).containsOnly(TAG_VALUE_FOO);
  }

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceWithMultipleTags(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processDefinitionId = "test-process-" + nextKey();

    // Create process instances with different tag combinations
    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b -> b.processDefinitionId(processDefinitionId).tags(Set.of(TAG_VALUE_FOO))));

    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b -> b.processDefinitionId(processDefinitionId).tags(Set.of(TAG_VALUE_BAR))));

    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId(processDefinitionId)
                    .tags(Set.of(TAG_VALUE_FOO, TAG_VALUE_BAR))));

    // Search for both tags - should only return the instance with BOTH tags (AND logic)
    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(
                        f ->
                            f.processDefinitionIds(processDefinitionId)
                                .tags(Set.of(TAG_VALUE_FOO, TAG_VALUE_BAR)))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items().getFirst().tags())
        .containsExactlyInAnyOrder(TAG_VALUE_FOO, TAG_VALUE_BAR);
  }
}
