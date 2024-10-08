/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
import static io.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.optimize.dto.optimize.query.MetadataDto;
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
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.6.0";
  private static final String INTERMEDIATE_VERSION = "2.6.1";
  private static final String TO_VERSION = "2.7.0";

  @Test
  public void executeCreateIndexWithAliasStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(new CreateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final String versionedIndexName =
        getIndexNameService()
            .getOptimizeIndexNameWithVersionForAllIndicesOf(TEST_INDEX_WITH_UPDATED_MAPPING_V2);

    assertThat(databaseIntegrationTestExtension.indexExists(versionedIndexName, true)).isTrue();
    assertThatIndexIsSetAsWriteIndex(TEST_INDEX_WITH_UPDATED_MAPPING_V2);
  }

  @Test
  public void executeCreateTemplateBasedIndexWithAliasStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThatIndexIsSetAsWriteIndex(TEST_INDEX_WITH_TEMPLATE_V1);
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep() {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V1)))
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
        .isTrue();
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep_preexistingIndexWithoutAliasWriteIndexFlag() {
    // given
    final String aliasForIndex =
        getIndexNameService().getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);

    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
        .isTrue();
    // even though not being set before the writeIndex flag is now set
    assertThatIndexIsSetAsWriteIndex(TEST_INDEX_WITH_UPDATED_MAPPING_V2);
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithAliasFromTemplateStep() {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1)))
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
        .isTrue();

    final Map<String, ?> mappingFields =
        databaseIntegrationTestExtension.getMappingFields(
            TEST_INDEX_WITH_UPDATED_MAPPING_V2.getIndexName());
    assertThat(mappingFields).containsKey("email");
  }

  @SneakyThrows
  @Test
  public void
      executeUpdateIndexFromTemplateStep_preexistingIndexWasNotFromTemplateAndLackedAliasWriteIndexFlag() {
    // given
    final String aliasForIndex =
        getIndexNameService().getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);

    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
        .isTrue();

    final Map<String, ?> mappingFields =
        databaseIntegrationTestExtension.getMappingFields(
            TEST_INDEX_WITH_UPDATED_MAPPING_V2.getIndexName());
    assertThat(mappingFields).containsKey("email");

    // even though not being set before the writeIndex flag is now set
    assertThatIndexIsSetAsWriteIndex(TEST_INDEX_WITH_UPDATED_MAPPING_V2, INDEX_SUFFIX_PRE_ROLLOVER);
  }

  @SneakyThrows
  @Test
  public void
      executeUpdateIndexFromTemplateStep_preexistingIndexWasNotFromTemplateAndHadWriteAndReadAlias() {
    // given
    final String aliasForIndex =
        getIndexNameService().getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    final String readOnlyAliasForIndex =
        getIndexNameService().getOptimizeIndexAliasForIndex("im-read-only");

    Map<String, Boolean> aliases =
        Map.of(
            aliasForIndex, true,
            readOnlyAliasForIndex, false);

    databaseIntegrationTestExtension.createIndex(
        getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1),
        aliases,
        (DefaultIndexMappingCreator) TEST_INDEX_V1);

    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
        .isTrue();

    assertThatIndexIsSetAsWriteIndex(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(databaseIntegrationTestExtension.isAliasReadOnly(readOnlyAliasForIndex)).isTrue();
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithTemplateAfterRolloverStep() {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(INTERMEDIATE_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1)))
            .build();

    upgradeProcedure.performUpgrade(buildIndexPlan);

    getPrefixAwareClient().triggerRollover(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);

    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(INTERMEDIATE_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)))
            .build();

    // when update index after rollover
    upgradeProcedure.performUpgrade(upgradePlan);

    // then optimize-users write alias points to updated users index
    final String expectedSuffixAfterRollover = "-000002";
    final String indexAlias =
        getIndexNameService()
            .getOptimizeIndexAliasForIndex(
                TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    final Map<String, Set<String>> aliasMap = getAliasMap(indexAlias);
    final List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    final Map<String, ?> mappingFields =
        databaseIntegrationTestExtension.getMappingFields(
            TEST_INDEX_WITH_UPDATED_MAPPING_V2.getIndexName());
    assertThat(mappingFields).containsKey("email");
    assertThat(aliasMap.keySet()).hasSize(2);
    assertThat(indicesWithWriteAlias).hasSize(1);
    assertThat(indicesWithWriteAlias.get(0)).contains(expectedSuffixAfterRollover);

    // old template is gone
    assertThat(
            databaseIntegrationTestExtension.templateExists(
                getIndexNameService()
                    .getOptimizeIndexTemplateNameWithVersion(TEST_INDEX_WITH_TEMPLATE_V1)))
        .isFalse();
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    List<UserTestDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            TEST_INDEX_V2.getIndexName(), UserTestDto.class);

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
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
            .addUpgradeStep(buildUpdateTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    List<UserTestDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            TEST_INDEX_V2.getIndexName(), UserTestDto.class);

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
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
            .addUpgradeStep(buildDeleteTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    List<UserTestDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            TEST_INDEX_V2.getIndexName(), UserTestDto.class);

    assertThat(result).isEmpty();
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
            .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_V2))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V2)))
        .isFalse();
  }

  @Test
  public void executeDeleteIndexStep_rolledOverIndex() throws Exception {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(INTERMEDIATE_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1)))
            .build();

    upgradeProcedure.performUpgrade(buildIndexPlan);

    getPrefixAwareClient().triggerRollover(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);

    // then two indices exist after the rollover
    boolean indicesExist =
        databaseIntegrationTestExtension.indexExists(
            getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_V1));
    assertThat(indicesExist).isTrue();
    final Set<String> response = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_V1);
    assertThat(response).hasSize(2);

    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(INTERMEDIATE_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_WITH_TEMPLATE_V1))
            .build();

    // when update index after rollover
    upgradeProcedure.performUpgrade(upgradePlan);

    // then both the initial and rolled over index are deleted
    indicesExist =
        databaseIntegrationTestExtension.indexExists(
            getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_V1));
    assertThat(indicesExist).isFalse();
  }

  @Test
  public void executeUpgradeMappingIndexStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
            .addUpgradeStep(
                applyLookupSkip(new UpdateMappingIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final Map<String, ?> mappingFields =
        databaseIntegrationTestExtension.getMappingFields(
            TEST_INDEX_WITH_UPDATED_MAPPING_V2.getIndexName());
    assertThat(mappingFields).containsKey("email");
  }

  @Test
  public void fieldRenameWithoutRemovingOldFieldAbortsUpgrade() throws IOException {
    // given
    createOptimizeIndexWithTypeAndVersion((DefaultIndexMappingCreator) TEST_INDEX_RENAME_FIELD, 1);

    databaseIntegrationTestExtension.insertTestDocuments(1, "users", "{\"name\": \"yuri_loza\"}");
    getPrefixAwareClient().refresh("*");

    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(
                applyLookupSkip(
                    new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2, "def foo = \"noop\";")))
            .build();

    // when
    assertThrows(UpgradeRuntimeException.class, () -> upgradeProcedure.performUpgrade(upgradePlan));
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    // given
    UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    List<MetadataDto> result =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            METADATA_INDEX.getIndexName(), MetadataDto.class);

    assertThat(result)
        .hasSize(1)
        .allSatisfy(metadata -> assertThat(metadata.getSchemaVersion()).isEqualTo(TO_VERSION));
  }

  private void assertThatIndexIsSetAsWriteIndex(final IndexMappingCreator mappingCreator) {
    assertThatIndexIsSetAsWriteIndex(mappingCreator, "");
  }

  private void assertThatIndexIsSetAsWriteIndex(
      final IndexMappingCreator mappingCreator, String expectedSuffix) {
    final List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(mappingCreator.getIndexName());
    assertThat(indicesWithWriteAlias)
        .containsExactly(
            getIndexNameService().getOptimizeIndexNameWithVersion(mappingCreator) + expectedSuffix);
  }

  private void createIndexWithoutWriteIndexFlagOnAlias(final String aliasForIndex)
      throws IOException {
    databaseIntegrationTestExtension.createIndex(
        getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1),
        aliasForIndex,
        (DefaultIndexMappingCreator) TEST_INDEX_V1,
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
