/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.variable;
//
// import static
// io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
// import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
// import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
// import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
// import io.camunda.optimize.service.events.rollover.ExternalProcessVariableIndexRolloverService;
// import java.util.List;
// import java.util.stream.IntStream;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
//
// public class ExternalProcessVariableRolloverIT extends AbstractPlatformIT {
//
//   private static final int NUMBER_OF_VARIABLES_IN_BATCH = 10;
//   private static final String EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER = "-000002";
//   private static final String EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER = "-000003";
//
//   @BeforeEach
//   @AfterEach
//   public void cleanUpExternalVariableIndices() {
//     databaseIntegrationTestExtension.deleteAllExternalVariableIndices();
//     embeddedOptimizeExtension
//         .getDatabaseSchemaManager()
//         .createOrUpdateOptimizeIndex(
//             embeddedOptimizeExtension.getOptimizeDatabaseClient(),
//             new ExternalProcessVariableIndexES());
//   }
//
//   @Test
//   public void noRolloverIfConditionsNotMet() {
//     // given
//     ingestExternalVariables();
//
//     // when
//     final List<String> rolledOverIndexAliases =
//         getExternalProcessVariableIndexRollover().triggerRollover();
//
//     // then
//     assertThat(rolledOverIndexAliases).isEmpty();
//     assertThat(extractIndicesWithWriteAlias())
//         .singleElement()
//         .isEqualTo(getExpectedIndexNameBeforeRollover());
//     assertThat(extractIndicesWithReadOnlyAlias()).isEmpty();
//     assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH);
//   }
//
//   @Test
//   public void singleRollover() {
//     // given
//     ingestExternalVariables();
//     setMaxIndexSizeGBForExternalVariableIndexRollover(0);
//
//     // when
//     final List<String> rolledOverIndexAliases =
//         getExternalProcessVariableIndexRollover().triggerRollover();
//
//     // then
//     assertThat(rolledOverIndexAliases)
//         .singleElement()
//         .isEqualTo(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
//     assertThat(extractIndicesWithWriteAlias())
//         .singleElement()
//         .isEqualTo(getExpectedIndexNameAfterFirstRollover());
//     assertThat(extractIndicesWithReadOnlyAlias())
//         .singleElement()
//         .isEqualTo(getExpectedIndexNameBeforeRollover());
//     assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH);
//   }
//
//   @Test
//   public void multipleRollovers() {
//     // given
//     ingestExternalVariables();
//     setMaxIndexSizeGBForExternalVariableIndexRollover(0);
//
//     // when
//     final List<String> rolledOverIndexAliasesAfterFirstRollover =
//         getExternalProcessVariableIndexRollover().triggerRollover();
//
//     // then
//     assertThat(rolledOverIndexAliasesAfterFirstRollover)
//         .singleElement()
//         .isEqualTo(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
//     assertThat(extractIndicesWithWriteAlias())
//         .singleElement()
//         .isEqualTo(getExpectedIndexNameAfterFirstRollover());
//     assertThat(extractIndicesWithReadOnlyAlias())
//         .singleElement()
//         .isEqualTo(getExpectedIndexNameBeforeRollover());
//     assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH);
//
//     // when
//     ingestExternalVariables();
//     final List<String> rolledOverIndexAliasesAfterSecondRollover =
//         getExternalProcessVariableIndexRollover().triggerRollover();
//
//     // then
//     assertThat(rolledOverIndexAliasesAfterSecondRollover)
//         .singleElement()
//         .isEqualTo(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
//     assertThat(extractIndicesWithWriteAlias())
//         .singleElement()
//         .isEqualTo(getExpectedIndexNameAfterSecondRollover());
//     assertThat(extractIndicesWithReadOnlyAlias())
//         .hasSize(2)
//         .containsExactlyInAnyOrder(
//             getExpectedIndexNameBeforeRollover(), getExpectedIndexNameAfterFirstRollover());
//     assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH * 2);
//     assertThat(
//             databaseIntegrationTestExtension.getDocumentCountOf(
//                 getExpectedIndexNameBeforeRollover()))
//         .isEqualTo(NUMBER_OF_VARIABLES_IN_BATCH);
//     assertThat(
//             databaseIntegrationTestExtension.getDocumentCountOf(
//                 getExpectedIndexNameAfterFirstRollover()))
//         .isEqualTo(NUMBER_OF_VARIABLES_IN_BATCH);
//     assertThat(
//             databaseIntegrationTestExtension.getDocumentCountOf(
//                 getExpectedIndexNameAfterSecondRollover()))
//         .isZero();
//   }
//
//   private void ingestExternalVariables() {
//     final List<ExternalProcessVariableRequestDto> variables =
//         IntStream.range(0, NUMBER_OF_VARIABLES_IN_BATCH)
//             .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
//             .toList();
//     ingestionClient.ingestVariables(variables);
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//   }
//
//   private void setMaxIndexSizeGBForExternalVariableIndexRollover(final int maxIndexSizeGB) {
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getVariableIndexRolloverConfiguration()
//         .setMaxIndexSizeGB(maxIndexSizeGB);
//   }
//
//   private ExternalProcessVariableIndexRolloverService getExternalProcessVariableIndexRollover() {
//     return embeddedOptimizeExtension.getExternalProcessVariableIndexRolloverService();
//   }
//
//   private List<ExternalProcessVariableDto> getAllStoredExternalProcessVariables() {
//     return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//         EXTERNAL_PROCESS_VARIABLE_INDEX_NAME, ExternalProcessVariableDto.class);
//   }
//
//   private List<String> extractIndicesWithWriteAlias() {
//     return databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(
//         EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
//   }
//
//   private List<String> extractIndicesWithReadOnlyAlias() {
//     return databaseIntegrationTestExtension.getAllIndicesWithReadOnlyAlias(
//         EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
//   }
//
//   private String getExpectedIndexNameBeforeRollover() {
//     return embeddedOptimizeExtension
//             .getIndexNameService()
//             .getOptimizeIndexTemplateNameWithVersion(new ExternalProcessVariableIndexES())
//         + INDEX_SUFFIX_PRE_ROLLOVER;
//   }
//
//   private String getExpectedIndexNameAfterFirstRollover() {
//     return embeddedOptimizeExtension
//             .getIndexNameService()
//             .getOptimizeIndexTemplateNameWithVersion(new ExternalProcessVariableIndexES())
//         + EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER;
//   }
//
//   private String getExpectedIndexNameAfterSecondRollover() {
//     return embeddedOptimizeExtension
//             .getIndexNameService()
//             .getOptimizeIndexTemplateNameWithVersion(new ExternalProcessVariableIndexES())
//         + EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER;
//   }
// }
