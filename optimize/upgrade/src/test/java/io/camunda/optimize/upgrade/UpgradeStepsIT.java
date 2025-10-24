/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_SUFFIX;
import static io.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.db.indices.UserTestDto;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.6.0";
  private static final String INTERMEDIATE_VERSION = "2.6.1";
  private static final String TO_VERSION = "2.7.0";

  @Test
  public void executeCreateIndexWithAliasStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexWithUpdatedMappingV2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final String versionedIndexName =
        getIndexNameService()
            .getOptimizeIndexNameWithVersionForAllIndicesOf(testIndexWithUpdatedMappingV2);

    assertThat(databaseIntegrationTestExtension.indexExists(versionedIndexName, true)).isTrue();
    assertThatIndexIsSetAsWriteIndex(testIndexWithUpdatedMappingV2);
  }

  @Test
  public void executeCreateTemplateBasedIndexWithAliasStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexWithTemplateV1)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThatIndexIsSetAsWriteIndex(testIndexWithTemplateV1);
  }

  @Test
  public void executeUpdateIndexStep() {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV1)))
            .addUpgradeStep(applyLookupSkip(new UpdateIndexStep(testIndexWithUpdatedMappingV2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(testIndexWithUpdatedMappingV2)))
        .isTrue();
  }

  @Test
  public void executeUpdateIndexStepPreexistingIndexWithoutAliasWriteIndexFlag() {
    // given
    final String aliasForIndex =
        getIndexNameService().getOptimizeIndexAliasForIndex(testIndexV1.getIndexName());
    try {
      createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }

    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new UpdateIndexStep(testIndexWithUpdatedMappingV2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(testIndexWithUpdatedMappingV2)))
        .isTrue();
    // even though not being set before the writeIndex flag is now set
    assertThatIndexIsSetAsWriteIndex(testIndexWithUpdatedMappingV2);
  }

  @Test
  public void
      executeUpdateIndexFromTemplateStepPreexistingIndexWasNotFromTemplateAndLackedAliasWriteIndexFlag() {
    // given
    final String aliasForIndex =
        getIndexNameService().getOptimizeIndexAliasForIndex(testIndexV1.getIndexName());
    try {
      createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }

    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(testIndexWithTemplateUpdatedMappingV2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(testIndexWithTemplateUpdatedMappingV2)))
        .isTrue();

    final Map<String, ?> mappingFields;
    try {
      mappingFields =
          databaseIntegrationTestExtension.getMappingFields(
              testIndexWithUpdatedMappingV2.getIndexName());
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
    assertThat(mappingFields).containsKey("email");

    // even though not being set before the writeIndex flag is now set
    assertThatIndexIsSetAsWriteIndex(testIndexWithUpdatedMappingV2, INDEX_SUFFIX);
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV2)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.testIndexV1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<UserTestDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            testIndexV2.getIndexName(), UserTestDto.class);

    assertThat(result)
        .hasSize(1)
        .allSatisfy(
            user -> {
              assertThat(user.getUsername()).isEqualTo("admin");
              assertThat(user.getPassword()).isEqualTo("admin");
            });
  }

  @Test
  public void executeUpdateDataStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV2)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.testIndexV1))
            .addUpgradeStep(buildUpdateTestIndexDataStep(UpgradeStepsIT.testIndexV1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<UserTestDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            testIndexV2.getIndexName(), UserTestDto.class);

    assertThat(result)
        .hasSize(1)
        .allSatisfy(
            user -> {
              assertThat(user.getUsername()).isEqualTo("admin");
              assertThat(user.getPassword()).isEqualTo("admin1");
            });
  }

  @Test
  public void executeDeleteDataStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV2)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.testIndexV1))
            .addUpgradeStep(buildDeleteTestIndexDataStep(UpgradeStepsIT.testIndexV1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<UserTestDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            testIndexV2.getIndexName(), UserTestDto.class);

    assertThat(result).isEmpty();
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV2)))
            .addUpgradeStep(buildDeleteIndexStep(testIndexV2))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService().getOptimizeIndexNameWithVersion(testIndexV2)))
        .isFalse();
  }

  @Test
  public void executeDeleteIndexStepRolledOverIndex() throws Exception {
    // given rolled over users index
    final UpgradePlan buildIndexPlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(INTERMEDIATE_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexWithTemplateV1)))
            .build();

    upgradeProcedure.performUpgrade(buildIndexPlan);

    getPrefixAwareClient().triggerRollover(testIndexWithTemplateV1.getIndexName(), 0);

    // then two indices exist after the rollover
    boolean indicesExist =
        databaseIntegrationTestExtension.indexExists(
            getIndexNameService().getOptimizeIndexNameWithVersion(testIndexWithTemplateV1));
    assertThat(indicesExist).isTrue();
    final Set<String> response = getIndicesForMapping(testIndexWithTemplateV1);
    assertThat(response).hasSize(2);

    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(INTERMEDIATE_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(buildDeleteIndexStep(testIndexWithTemplateV1))
            .build();

    // when update index after rollover
    upgradeProcedure.performUpgrade(upgradePlan);

    // then both the initial and rolled over index are deleted
    indicesExist =
        databaseIntegrationTestExtension.indexExists(
            getIndexNameService().getOptimizeIndexNameWithVersion(testIndexWithTemplateV1));
    assertThat(indicesExist).isFalse();
  }

  @Test
  public void executeUpgradeMappingIndexStep() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV2)))
            .addUpgradeStep(
                applyLookupSkip(new UpdateMappingIndexStep(testIndexWithUpdatedMappingV2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final Map<String, ?> mappingFields =
        databaseIntegrationTestExtension.getMappingFields(
            testIndexWithUpdatedMappingV2.getIndexName());
    assertThat(mappingFields).containsKey("email");
  }

  @Test
  public void fieldRenameWithoutRemovingOldFieldAbortsUpgrade() throws IOException {
    // given
    createOptimizeIndexWithTypeAndVersion((DefaultIndexMappingCreator) testIndexRenameField, 1);

    databaseIntegrationTestExtension.insertTestDocuments(1, "users", "{\"name\": \"yuri_loza\"}");
    getPrefixAwareClient().refresh("*");

    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(
                    new UpdateIndexStep(testIndexWithUpdatedMappingV2, "def foo = \"noop\";")))
            .build();

    // when
    assertThatExceptionOfType(UpgradeRuntimeException.class)
        .isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    // given
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(testIndexV2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<MetadataDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            metadataIndex.getIndexName(), MetadataDto.class);

    assertThat(result)
        .hasSize(1)
        .allSatisfy(metadata -> assertThat(metadata.getSchemaVersion()).isEqualTo(TO_VERSION));
  }

  private void assertThatIndexIsSetAsWriteIndex(final IndexMappingCreator mappingCreator) {
    assertThatIndexIsSetAsWriteIndex(mappingCreator, "");
  }

  private void assertThatIndexIsSetAsWriteIndex(
      final IndexMappingCreator mappingCreator, final String expectedSuffix) {
    final List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(mappingCreator.getIndexName());
    assertThat(indicesWithWriteAlias)
        .containsExactly(
            getIndexNameService().getOptimizeIndexNameWithVersion(mappingCreator) + expectedSuffix);
  }

  private void createIndexWithoutWriteIndexFlagOnAlias(final String aliasForIndex)
      throws IOException {
    databaseIntegrationTestExtension.createIndex(
        getIndexNameService().getOptimizeIndexNameWithVersion(testIndexV1),
        aliasForIndex,
        (DefaultIndexMappingCreator) testIndexV1,
        false);
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  private DeleteIndexIfExistsStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return applyLookupSkip(
        new DeleteIndexIfExistsStep(indexMapping.getIndexName(), indexMapping.getVersion()));
  }

  private Map<String, Set<String>> getAliasMap(final String aliasName) throws IOException {
    return getPrefixAwareClient().getAliasesForIndexPattern(aliasName);
  }
}
