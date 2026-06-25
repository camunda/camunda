/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.variables;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionVariableNameLookupIT {

  @RdbmsTestTemplate
  public void shouldRecordVariableNameForProcessDefinition(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final long procDefKey = nextKey();
    final String varName = "amount-" + nextStringId();

    // when: two process instances each contribute one variable with the same name
    createInstanceWithVariable(rdbmsService, procDefKey, varName);
    createInstanceWithVariable(rdbmsService, procDefKey, varName);

    // then: only one entry exists (idempotent insert)
    assertThat(
            testApplication
                .getRdbmsService()
                .getVariableReader()
                .findLookupVariableNames(procDefKey))
        .containsExactly(varName);
  }

  @RdbmsTestTemplate
  public void shouldRecordDistinctVariableNamesForProcessDefinition(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final long procDefKey = nextKey();
    final String varNameA = "var-a-" + nextStringId();
    final String varNameB = "var-b-" + nextStringId();

    // when
    createInstanceWithVariable(rdbmsService, procDefKey, varNameA);
    createInstanceWithVariable(rdbmsService, procDefKey, varNameB);

    // then: both names are present
    assertThat(
            testApplication
                .getRdbmsService()
                .getVariableReader()
                .findLookupVariableNames(procDefKey))
        .containsExactlyInAnyOrder(varNameA, varNameB);
  }

  @RdbmsTestTemplate
  public void shouldNotInsertDuplicateLookupEntryWithinSameWriterSession(
      final CamundaRdbmsTestApplication testApplication) {
    // given: a single writer instance whose in-memory cache tracks seen (procDefKey, varName) pairs
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);
    final long procDefKey = nextKey();
    final String varName = "cached-" + nextStringId();

    // when: two different process instances each contribute a variable with the same name,
    // using the SAME writer (so the second create hits the in-memory cache instead of the DB)
    final var instance1 =
        ProcessInstanceFixtures.createRandomized(b -> b.processDefinitionKey(procDefKey));
    ProcessInstanceFixtures.createAndSaveProcessInstance(writers, instance1);
    writers
        .getVariableWriter()
        .create(
            VariableFixtures.createRandomized(
                b ->
                    b.processInstanceKey(instance1.processInstanceKey())
                        .processDefinitionKey(procDefKey)
                        .name(varName)));

    final var instance2 =
        ProcessInstanceFixtures.createRandomized(b -> b.processDefinitionKey(procDefKey));
    ProcessInstanceFixtures.createAndSaveProcessInstance(writers, instance2);
    writers
        .getVariableWriter()
        .create(
            VariableFixtures.createRandomized(
                b ->
                    b.processInstanceKey(instance2.processInstanceKey())
                        .processDefinitionKey(procDefKey)
                        .name(varName)));

    writers.flush();

    // then: the cache suppressed the second lookup INSERT — exactly one entry in the table
    assertThat(
            testApplication
                .getRdbmsService()
                .getVariableReader()
                .findLookupVariableNames(procDefKey))
        .containsExactly(varName);
  }

  @RdbmsTestTemplate
  public void shouldDeleteAllLookupEntriesWhenProcessDefinitionDeleted(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);

    final var processDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long procDefKey = processDef.processDefinitionKey();
    final String varNameA = "var-a-" + nextStringId();
    final String varNameB = "var-b-" + nextStringId();

    createInstanceWithVariable(rdbmsService, procDefKey, varNameA);
    createInstanceWithVariable(rdbmsService, procDefKey, varNameB);

    // when
    writers.getVariableWriter().deleteLookupByProcessDefinitionKeys(List.of(procDefKey), 1000);

    // then: all lookup entries are gone
    assertThat(
            testApplication
                .getRdbmsService()
                .getVariableReader()
                .findLookupVariableNames(procDefKey))
        .isEmpty();
  }

  // ---------------------------------------------------------------------------

  /**
   * Creates a persisted process instance and one variable linked to it (populating the lookup
   * table), and returns the process instance key.
   */
  private void createInstanceWithVariable(
      final RdbmsService rdbmsService, final long processDefinitionKey, final String varName) {

    final RdbmsWriters writers = rdbmsService.createWriter(0L);

    final var processInstance =
        ProcessInstanceFixtures.createRandomized(b -> b.processDefinitionKey(processDefinitionKey));
    ProcessInstanceFixtures.createAndSaveProcessInstance(writers, processInstance);

    final var variable =
        VariableFixtures.createRandomized(
            b -> b.processInstanceKey(processInstance.processInstanceKey()).name(varName));
    VariableFixtures.createAndSaveVariableWithProcessDefinition(
        rdbmsService, variable, processDefinitionKey);
  }
}
