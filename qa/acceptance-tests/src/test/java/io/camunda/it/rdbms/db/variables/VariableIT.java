/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.variables;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.countProtectionModes;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveRandomVariables;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveVariable;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveVariables;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.findProtectionModeCodes;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.prepareRandomVariablesAndReturnOne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.VariableDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.ProtectionModeDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.VariableDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.entities.VariableEntity.ProtectionMode;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class VariableIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final VariableDbModel randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final var instance =
        testApplication
            .getRdbmsService()
            .getVariableReader()
            .findOne(randomizedVariable.variableKey());

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(randomizedVariable, instance);
    assertThat(instance.fullValue()).isNull();
    assertThat(instance.isPreview()).isFalse();
  }

  @TestTemplate
  public void shouldSaveProtectionModes(final CamundaRdbmsTestApplication testApplication)
      throws SQLException {
    // given
    final var protectedVariable =
        createAndSaveVariable(
            testApplication.getRdbmsService(),
            b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));

    // when
    final var modes = findProtectionModeCodes(testApplication, protectedVariable.variableKey());

    // then
    assertThat(modes).containsExactly(ProtectionModeDbModel.REDACT.getCode());
  }

  @TestTemplate
  public void shouldSaveProtectionModesOfEachVariableInOneBatch(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var protectedVariable1 =
        VariableFixtures.createRandomized(
            b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    final var unprotectedVariable = VariableFixtures.createRandomized();
    final var protectedVariable2 =
        VariableFixtures.createRandomized(
            b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));

    // when
    createAndSaveVariables(
        rdbmsService, List.of(protectedVariable1, unprotectedVariable, protectedVariable2));

    // then
    assertThat(findProtectionModeCodes(testApplication, protectedVariable1.variableKey()))
        .containsExactly(ProtectionModeDbModel.REDACT.getCode());
    assertThat(findProtectionModeCodes(testApplication, protectedVariable2.variableKey()))
        .containsExactly(ProtectionModeDbModel.REDACT.getCode());
    assertThat(findProtectionModeCodes(testApplication, unprotectedVariable.variableKey()))
        .isEmpty();
  }

  @TestTemplate
  public void shouldNotSaveProtectionModesForUnprotectedVariable(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    // given
    final var variable = createAndSaveVariable(testApplication.getRdbmsService(), b -> b);

    // when / then
    assertThat(findProtectionModeCodes(testApplication, variable.variableKey())).isEmpty();
  }

  @TestTemplate
  public void shouldFindProtectionModes(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var variable =
        createAndSaveVariable(
            rdbmsService, b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));

    // when
    final var instance = rdbmsService.getVariableReader().findOne(variable.variableKey());

    // then
    assertThat(instance).isNotNull();
    assertThat(instance.protectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  @TestTemplate
  public void shouldFindNoProtectionModesForUnprotectedVariable(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var variable = prepareRandomVariablesAndReturnOne(testApplication);

    // when
    final var instance =
        testApplication.getRdbmsService().getVariableReader().findOne(variable.variableKey());

    // then
    assertThat(instance).isNotNull();
    assertThat(instance.protectionModes()).isEmpty();
  }

  @TestTemplate
  public void shouldFindProtectionModesOfEachVariableInSearch(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var name = "var-name-" + nextStringId();
    final var protectedVariable1 =
        VariableFixtures.createRandomized(
            b -> b.name(name).protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    final var unprotectedVariable = VariableFixtures.createRandomized(b -> b.name(name));
    final var protectedVariable2 =
        VariableFixtures.createRandomized(
            b -> b.name(name).protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    createAndSaveVariables(
        rdbmsService, List.of(protectedVariable1, unprotectedVariable, protectedVariable2));

    // when
    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(name).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(3);
    assertThat(searchResult.items())
        .extracting(VariableEntity::variableKey, VariableEntity::protectionModes)
        .containsExactlyInAnyOrder(
            tuple(protectedVariable1.variableKey(), Set.of(ProtectionMode.REDACT)),
            tuple(unprotectedVariable.variableKey(), Set.of()),
            tuple(protectedVariable2.variableKey(), Set.of(ProtectionMode.REDACT)));
  }

  @TestTemplate
  public void shouldPageProtectedVariablesWithoutLosingItems(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var name = "var-name-" + nextStringId();
    VariableFixtures.createAndSaveRandomVariables(
        rdbmsService, 5, b -> b.name(name).protectionModes(Set.of(ProtectionModeDbModel.REDACT)));

    // when
    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(name).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(2))));

    // then
    assertThat(searchResult.total()).isEqualTo(5);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items())
        .allSatisfy(
            item -> assertThat(item.protectionModes()).containsExactly(ProtectionMode.REDACT));
  }

  @TestTemplate
  public void shouldFilterByProtectionModeBeforePaging(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var name = "var-name-" + nextStringId();
    final var unprotected =
        VariableFixtures.createAndSaveRandomVariables(rdbmsService, 6, b -> b.name(name));
    final var protectedVariables =
        VariableFixtures.createAndSaveRandomVariables(
            rdbmsService,
            3,
            b -> b.name(name).protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    final var filter =
        new VariableFilter.Builder().names(name).protectionModes(ProtectionMode.REDACT).build();

    // when
    final var firstPage =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    filter, VariableSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(2))));
    final var secondPage =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    filter, VariableSort.of(b -> b), SearchQueryPage.of(b -> b.from(2).size(2))));

    // then
    assertThat(unprotected).hasSize(6);
    assertThat(firstPage.total()).isEqualTo(3);
    assertThat(firstPage.items()).hasSize(2);
    assertThat(secondPage.total()).isEqualTo(3);
    assertThat(secondPage.items()).hasSize(1);
    assertThat(
            Stream.concat(firstPage.items().stream(), secondPage.items().stream())
                .map(VariableEntity::variableKey))
        .containsExactlyInAnyOrderElementsOf(
            protectedVariables.stream().map(VariableDbModel::variableKey).toList());
  }

  @TestTemplate
  public void shouldCombineProtectionModeFilterWithOtherFilters(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var name = "var-name-" + nextStringId();
    final var otherName = "var-name-" + nextStringId();
    final var matching =
        VariableFixtures.createRandomized(
            b -> b.name(name).protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    final var protectedWithOtherName =
        VariableFixtures.createRandomized(
            b -> b.name(otherName).protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    final var unprotectedWithName = VariableFixtures.createRandomized(b -> b.name(name));
    createAndSaveVariables(
        rdbmsService, List.of(matching, protectedWithOtherName, unprotectedWithName));

    // when
    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder()
                        .names(name)
                        .protectionModes(ProtectionMode.REDACT)
                        .build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items())
        .extracting(VariableEntity::variableKey)
        .containsExactly(matching.variableKey());
  }

  @TestTemplate
  public void shouldDeleteProtectionModesWithVariable(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var protectedVariable =
        createAndSaveVariable(
            rdbmsService, b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));
    assertThat(findProtectionModeCodes(testApplication, protectedVariable.variableKey()))
        .hasSize(1);

    // when
    rdbmsService
        .createWriter(PARTITION_ID)
        .getVariableWriter()
        .deleteProcessInstanceRelatedData(List.of(protectedVariable.processInstanceKey()), 10);

    // then
    assertThat(countProtectionModes(testApplication, protectedVariable.variableKey())).isZero();
  }

  @TestTemplate
  public void shouldAddProtectionModesOnUpdate(final CamundaRdbmsTestApplication testApplication)
      throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var variable = createAndSaveVariable(rdbmsService, b -> b);

    // when
    updateProtectionModes(rdbmsService, variable, Set.of(ProtectionModeDbModel.REDACT));

    // then
    assertThat(findProtectionModeCodes(testApplication, variable.variableKey()))
        .containsExactly(ProtectionModeDbModel.REDACT.getCode());
  }

  @TestTemplate
  public void shouldRemoveProtectionModesOnUpdate(final CamundaRdbmsTestApplication testApplication)
      throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var variable =
        createAndSaveVariable(
            rdbmsService, b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));

    // when
    updateProtectionModes(rdbmsService, variable, Set.of());

    // then
    assertThat(findProtectionModeCodes(testApplication, variable.variableKey())).isEmpty();
  }

  @TestTemplate
  public void shouldKeepProtectionModesOnUpdateWithSameModes(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var modes = Set.of(ProtectionModeDbModel.REDACT);
    final var variable = createAndSaveVariable(rdbmsService, b -> b.protectionModes(modes));

    // when
    updateProtectionModes(rdbmsService, variable, modes);

    // then
    assertThat(findProtectionModeCodes(testApplication, variable.variableKey()))
        .containsExactly(ProtectionModeDbModel.REDACT.getCode());
  }

  @TestTemplate
  public void shouldApplyLastProtectionModesOfRepeatedUpdatesInOneFlush(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var redact = Set.of(ProtectionModeDbModel.REDACT);
    final var variableToUnprotect =
        createAndSaveVariable(rdbmsService, b -> b.protectionModes(redact));
    final var variableToProtect = createAndSaveVariable(rdbmsService, b -> b);
    final var variableKeptProtected =
        createAndSaveVariable(rdbmsService, b -> b.protectionModes(redact));

    // when
    final var writer = rdbmsService.createWriter(PARTITION_ID);
    writer.getVariableWriter().update(withModes(variableToUnprotect, redact));
    writer.getVariableWriter().update(withModes(variableToUnprotect, Set.of()));
    writer.getVariableWriter().update(withModes(variableToProtect, Set.of()));
    writer.getVariableWriter().update(withModes(variableToProtect, redact));
    writer.getVariableWriter().update(withModes(variableKeptProtected, redact));
    writer.getVariableWriter().update(withModes(variableKeptProtected, redact));
    writer.flush();

    // then
    assertThat(findProtectionModeCodes(testApplication, variableToUnprotect.variableKey()))
        .isEmpty();
    assertThat(findProtectionModeCodes(testApplication, variableToProtect.variableKey()))
        .containsExactly(ProtectionModeDbModel.REDACT.getCode());
    assertThat(findProtectionModeCodes(testApplication, variableKeptProtected.variableKey()))
        .containsExactly(ProtectionModeDbModel.REDACT.getCode());
  }

  @TestTemplate
  public void shouldApplyUpdatedProtectionModesOfVariableCreatedInSameFlush(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var variable =
        VariableFixtures.createRandomized(
            b -> b.protectionModes(Set.of(ProtectionModeDbModel.REDACT)));

    // when
    final var writer = rdbmsService.createWriter(PARTITION_ID);
    writer.getVariableWriter().create(variable);
    writer.getVariableWriter().update(withModes(variable, Set.of()));
    writer.flush();

    // then
    assertThat(findProtectionModeCodes(testApplication, variable.variableKey())).isEmpty();
  }

  private static VariableDbModel withModes(
      final VariableDbModel variable, final Set<ProtectionModeDbModel> modes) {
    return variable.copy(b -> ((VariableDbModelBuilder) b).protectionModes(modes));
  }

  private static void updateProtectionModes(
      final RdbmsService rdbmsService,
      final VariableDbModel variable,
      final Set<ProtectionModeDbModel> modes) {
    final var writer = rdbmsService.createWriter(PARTITION_ID);
    writer.getVariableWriter().update(withModes(variable, modes));
    writer.flush();
  }

  @TestTemplate
  public void shouldUpdateAndFindVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableDbModel randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final var newValue = "new value";
    final VariableDbModel updatedVariable =
        randomizedVariable.copy(b -> ((VariableDbModelBuilder) b).value(newValue));

    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);
    rdbmsWriters.getVariableWriter().update(updatedVariable);
    rdbmsWriters.flush();

    final var instance = rdbmsService.getVariableReader().findOne(randomizedVariable.variableKey());

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(updatedVariable, instance);
  }

  @TestTemplate
  public void shouldSaveAndFindBigVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String bigValue = generateRandomString(9000);
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.value(bigValue));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var instance = rdbmsService.getVariableReader().findOne(randomizedVariable.variableKey());

    assertThat(instance).isNotNull();
    assertThat(instance.isPreview()).isTrue();
    assertThat(instance.fullValue()).isEqualTo(bigValue);
    assertThat(instance.value()).hasSizeLessThan(instance.fullValue().length());
  }

  @TestTemplate
  public void shouldSaveAndFindLargeByteVariableByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String bigValue = "ä".repeat(9000);
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.value(bigValue));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var instance = rdbmsService.getVariableReader().findOne(randomizedVariable.variableKey());

    assertThat(instance).as("variable is not null").isNotNull();
    assertThat(instance.isPreview()).as("variable is preview").isTrue();
    assertThat(instance.fullValue()).as("fullValue is equal to bigValue").isEqualTo(bigValue);
    assertThat(instance.value())
        .as("value length is less than fullValueLength")
        .hasSizeLessThan(instance.fullValue().length());
  }

  @TestTemplate
  public void shouldFindVariableByProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final VariableDbModel randomizedVariable = VariableFixtures.createRandomized();
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder()
                        .processInstanceKeys(randomizedVariable.processInstanceKey())
                        .build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(randomizedVariable, instance);
  }

  @TestTemplate
  public void shouldFindVariableByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final VariableDbModel randomizedVariable = VariableFixtures.createRandomized();
    createAndSaveVariable(rdbmsService, randomizedVariable);
    createAndSaveRandomVariables(rdbmsService, b -> b);

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                VariableQuery.of(b -> b),
                CommonFixtures.resourceAccessChecksFromResourceIds(
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    randomizedVariable.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(searchResult.items().getFirst().variableKey())
        .isEqualTo(randomizedVariable.variableKey());
  }

  @TestTemplate
  public void shouldFindVariableByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final VariableDbModel randomizedVariable = VariableFixtures.createRandomized();
    createAndSaveVariable(rdbmsService, randomizedVariable);
    createAndSaveRandomVariables(rdbmsService, b -> b);

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                VariableQuery.of(b -> b),
                CommonFixtures.resourceAccessChecksFromTenantIds(randomizedVariable.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(searchResult.items().getFirst().variableKey())
        .isEqualTo(randomizedVariable.variableKey());
  }

  @TestTemplate
  public void shouldFindAllVariablesPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllVariablesPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String varName = "var-name-" + nextStringId();
    VariableFixtures.createAndSaveRandomVariables(rdbmsService, 120, b -> b.name(varName));

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllVariablesPageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindVariableWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableDbReader variableReader = rdbmsService.getVariableReader();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(varName));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var searchResult =
        variableReader.search(
            new VariableQuery(
                new VariableFilter.Builder()
                    .variableKeys(randomizedVariable.variableKey())
                    .processInstanceKeys(randomizedVariable.processInstanceKey())
                    .names(varName)
                    .tenantIds(randomizedVariable.tenantId())
                    .build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().variableKey())
        .isEqualTo(randomizedVariable.variableKey());
  }

  @TestTemplate
  public void shouldFindVariableWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableDbReader variableReader = rdbmsService.getVariableReader();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();

    final var sort =
        VariableSort.of(s -> s.scopeKey().asc().value().asc().processInstanceKey().desc());
    final var searchResult =
        variableReader.search(
            VariableQuery.of(
                b -> b.filter(f -> f.names(varName)).sort(sort).page(p -> p.from(0).size(20))));

    final var firstPage =
        variableReader.search(
            VariableQuery.of(
                b -> b.filter(f -> f.names(varName)).sort(sort).page(p -> p.size(15))));

    final var nextPage =
        variableReader.search(
            VariableQuery.of(
                b ->
                    b.filter(f -> f.names(varName))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final VariableDbReader reader = rdbmsService.getVariableReader();

    final var tenantId = nextStringId();
    final var item1 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));
    final var item2 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));
    final var item3 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));

    // when
    final int deleted =
        rdbmsWriters
            .getVariableWriter()
            .deleteProcessInstanceRelatedData(List.of(item2.processInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            VariableQuery.of(
                b ->
                    b.filter(f -> f.tenantIds(tenantId))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(VariableEntity::variableKey))
        .containsExactlyInAnyOrder(item1.variableKey(), item3.variableKey());
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final VariableDbReader reader = rdbmsService.getVariableReader();

    final var tenantId = nextStringId();
    final var item1 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));
    final var item2 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));
    final var item3 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));

    // when
    final int deleted =
        rdbmsWriters
            .getVariableWriter()
            .deleteRootProcessInstanceRelatedData(List.of(item2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            VariableQuery.of(
                b ->
                    b.filter(f -> f.tenantIds(tenantId))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(VariableEntity::variableKey))
        .containsExactlyInAnyOrder(item1.variableKey(), item3.variableKey());
  }

  private void assertVariableDbModelEqualToEntity(
      final VariableDbModel dbModel, final VariableEntity entity) {
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields("bpmnProcessId", "processDefinitionId")
        .isEqualTo(dbModel);
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionId());
  }
}
