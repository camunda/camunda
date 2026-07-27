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
import io.camunda.search.query.VariableNameQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionVariableNameLookupIT {

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  // ===== searchVariableNames =====

  @TestTemplate
  public void shouldSearchVariableNamesOrderedAlphabeticallyAndCappedAtLimit(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);
    final var processDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long procDefKey = processDef.processDefinitionKey();

    createInstanceWithVariable(rdbmsService, procDefKey, "charlie");
    createInstanceWithVariable(rdbmsService, procDefKey, "alpha");
    createInstanceWithVariable(rdbmsService, procDefKey, "bravo");

    // when
    final var names =
        rdbmsService
            .getVariableReader()
            .searchVariableNames(
                VariableNameQuery.of(
                    b -> b.filter(f -> f.processDefinitionKeys(procDefKey)).page(p -> p.size(2))),
                authorizedFor(processDef.processDefinitionId(), processDef.tenantId()));

    // then: alphabetical order, capped at limit
    assertThat(names).containsExactly("alpha", "bravo");
  }

  @TestTemplate
  public void shouldSearchVariableNamesNarrowedByPrefix(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);
    final var processDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long procDefKey = processDef.processDefinitionKey();

    createInstanceWithVariable(rdbmsService, procDefKey, "amount");
    createInstanceWithVariable(rdbmsService, procDefKey, "amendment");
    createInstanceWithVariable(rdbmsService, procDefKey, "total");

    // when
    final var names =
        rdbmsService
            .getVariableReader()
            .searchVariableNames(
                VariableNameQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.processDefinitionKeys(procDefKey)
                                    .nameOperations(
                                        io.camunda.search.filter.Operation.like("am*")))),
                authorizedFor(processDef.processDefinitionId(), processDef.tenantId()));

    // then
    assertThat(names).containsExactly("amendment", "amount");
  }

  @TestTemplate
  public void shouldEscapeLiteralWildcardCharactersInPrefix(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);
    final var processDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long procDefKey = processDef.processDefinitionKey();

    createInstanceWithVariable(rdbmsService, procDefKey, "50*off");
    createInstanceWithVariable(rdbmsService, procDefKey, "50xoff");

    // when: the literal asterisk must be escaped, not treated as the wildcard character
    final var names =
        rdbmsService
            .getVariableReader()
            .searchVariableNames(
                VariableNameQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.processDefinitionKeys(procDefKey)
                                    .nameOperations(
                                        io.camunda.search.filter.Operation.like("50\\**")))),
                authorizedFor(processDef.processDefinitionId(), processDef.tenantId()));

    // then
    assertThat(names).containsExactly("50*off");
  }

  @TestTemplate
  public void shouldReturnEmptyListWhenCallerUnauthorizedOnProcessDefinition(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);
    final var processDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long procDefKey = processDef.processDefinitionKey();

    createInstanceWithVariable(rdbmsService, procDefKey, "amount");

    // when: caller is authorized on a different process definition
    final var names =
        rdbmsService
            .getVariableReader()
            .searchVariableNames(
                VariableNameQuery.of(b -> b.filter(f -> f.processDefinitionKeys(procDefKey))),
                authorizedFor("some-other-process", processDef.tenantId()));

    // then: no names leaked
    assertThat(names).isEmpty();
  }

  @TestTemplate
  public void shouldReturnEmptyListWhenCallerUnauthorizedOnTenant(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters writers = rdbmsService.createWriter(0L);
    final var processDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long procDefKey = processDef.processDefinitionKey();

    createInstanceWithVariable(rdbmsService, procDefKey, "amount");

    // when: caller is authorized on the correct process definition but a different tenant
    final var names =
        rdbmsService
            .getVariableReader()
            .searchVariableNames(
                VariableNameQuery.of(b -> b.filter(f -> f.processDefinitionKeys(procDefKey))),
                authorizedFor(processDef.processDefinitionId(), "some-other-tenant"));

    // then: no names leaked across tenants
    assertThat(names).isEmpty();
  }

  @TestTemplate
  public void shouldReturnEmptyListWhenNoProcessDefinitionKeyGiven(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // when
    final var names =
        rdbmsService
            .getVariableReader()
            .searchVariableNames(
                VariableNameQuery.of(b -> b), authorizedFor("any-process", "any-tenant"));

    // then
    assertThat(names).isEmpty();
  }

  private ResourceAccessChecks authorizedFor(
      final String processDefinitionId, final String tenantId) {
    return ResourceAccessChecks.of(
        AuthorizationCheck.enabled(
            RequiredAuthorization.of(
                a ->
                    a.processDefinition()
                        .readProcessInstance()
                        .resourceIds(List.of(processDefinitionId)))),
        TenantCheck.enabled(List.of(tenantId)));
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
