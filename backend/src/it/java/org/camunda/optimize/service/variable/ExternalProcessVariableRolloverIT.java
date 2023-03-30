/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.variable;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex;
import org.camunda.optimize.service.events.rollover.ExternalProcessVariableIndexRolloverService;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_SUFFIX_PRE_ROLLOVER;

public class ExternalProcessVariableRolloverIT extends AbstractIT {
  private static final int NUMBER_OF_VARIABLES_IN_BATCH = 10;
  private static final String EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER = "-000002";
  private static final String EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER = "-000003";

  @BeforeEach
  @AfterEach
  public void cleanUpExternalVariableIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalVariableIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new ExternalProcessVariableIndex()
    );
  }

  @Test
  public void noRolloverIfConditionsNotMet() {
    // given
    ingestExternalVariables();

    // when
    final List<String> rolledOverIndexAliases = getExternalProcessVariableIndexRollover().triggerRollover();

    // then
    final Map<String, Set<AliasMetadata>> aliasMap = getAllExternalVariableIndexAliasInfo();
    assertThat(rolledOverIndexAliases).isEmpty();
    assertThat(extractIndicesWithWriteAlias(aliasMap))
      .singleElement()
      .isEqualTo(getExpectedIndexNameBeforeRollover());
    assertThat(extractIndicesWithReadOnlyAlias(aliasMap)).isEmpty();
    assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH);
  }

  @Test
  public void singleRollover() {
    // given
    ingestExternalVariables();
    setMaxIndexSizeGBForExternalVariableIndexRollover(0);

    // when
    final List<String> rolledOverIndexAliases = getExternalProcessVariableIndexRollover().triggerRollover();

    // then
    final Map<String, Set<AliasMetadata>> aliasMap = getAllExternalVariableIndexAliasInfo();
    assertThat(rolledOverIndexAliases).singleElement().isEqualTo(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
    assertThat(extractIndicesWithWriteAlias(aliasMap))
      .singleElement()
      .isEqualTo(getExpectedIndexNameAfterFirstRollover());
    assertThat(extractIndicesWithReadOnlyAlias(aliasMap))
      .singleElement()
      .isEqualTo(getExpectedIndexNameBeforeRollover());
    assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH);
  }

  @Test
  public void multipleRollovers() {
    // given
    ingestExternalVariables();
    setMaxIndexSizeGBForExternalVariableIndexRollover(0);

    // when
    final List<String> rolledOverIndexAliasesAfterFirstRollover =
      getExternalProcessVariableIndexRollover().triggerRollover();

    // then
    Map<String, Set<AliasMetadata>> aliasMap = getAllExternalVariableIndexAliasInfo();
    assertThat(rolledOverIndexAliasesAfterFirstRollover)
      .singleElement()
      .isEqualTo(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
    assertThat(extractIndicesWithWriteAlias(aliasMap))
      .singleElement()
      .isEqualTo(getExpectedIndexNameAfterFirstRollover());
    assertThat(extractIndicesWithReadOnlyAlias(aliasMap))
      .singleElement()
      .isEqualTo(getExpectedIndexNameBeforeRollover());
    assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH);

    // when
    ingestExternalVariables();
    final List<String> rolledOverIndexAliasesAfterSecondRollover =
      getExternalProcessVariableIndexRollover().triggerRollover();
    aliasMap = getAllExternalVariableIndexAliasInfo();

    // then
    assertThat(rolledOverIndexAliasesAfterSecondRollover)
      .singleElement()
      .isEqualTo(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
    assertThat(extractIndicesWithWriteAlias(aliasMap))
      .singleElement()
      .isEqualTo(getExpectedIndexNameAfterSecondRollover());
    assertThat(extractIndicesWithReadOnlyAlias(aliasMap))
      .hasSize(2)
      .containsExactlyInAnyOrder(getExpectedIndexNameBeforeRollover(), getExpectedIndexNameAfterFirstRollover());
    assertThat(getAllStoredExternalProcessVariables()).hasSize(NUMBER_OF_VARIABLES_IN_BATCH * 2);
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(getExpectedIndexNameBeforeRollover()))
      .isEqualTo(NUMBER_OF_VARIABLES_IN_BATCH);
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(getExpectedIndexNameAfterFirstRollover()))
      .isEqualTo(NUMBER_OF_VARIABLES_IN_BATCH);
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(getExpectedIndexNameAfterSecondRollover()))
      .isZero();
  }

  private void ingestExternalVariables() {
    final List<ExternalProcessVariableRequestDto> variables = IntStream.range(0, NUMBER_OF_VARIABLES_IN_BATCH)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());
    ingestionClient.ingestVariables(variables);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void setMaxIndexSizeGBForExternalVariableIndexRollover(final int maxIndexSizeGB) {
    embeddedOptimizeExtension.getConfigurationService()
      .getVariableIndexRolloverConfiguration()
      .setMaxIndexSizeGB(maxIndexSizeGB);
  }

  private ExternalProcessVariableIndexRolloverService getExternalProcessVariableIndexRollover() {
    return embeddedOptimizeExtension.getExternalProcessVariableIndexRolloverService();
  }

  private List<ExternalProcessVariableDto> getAllStoredExternalProcessVariables() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      EXTERNAL_PROCESS_VARIABLE_INDEX_NAME, ExternalProcessVariableDto.class
    );
  }

  @SneakyThrows
  private Map<String, Set<AliasMetadata>> getAllExternalVariableIndexAliasInfo() {
    final String aliasNameWithPrefix = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
    final GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasNameWithPrefix);
    return embeddedOptimizeExtension.getOptimizeElasticClient().getAlias(aliasesRequest).getAliases();
  }

  private List<String> extractIndicesWithWriteAlias(final Map<String, Set<AliasMetadata>> indexNameToAliasMap) {
    return indexNameToAliasMap.keySet()
      .stream()
      .filter(index -> indexNameToAliasMap.get(index).stream().anyMatch(AliasMetadata::writeIndex))
      .collect(toList());
  }

  private List<String> extractIndicesWithReadOnlyAlias(final Map<String, Set<AliasMetadata>> indexNameToAliasMap) {
    return indexNameToAliasMap.keySet()
      .stream()
      .filter(index -> indexNameToAliasMap.get(index).stream().anyMatch(alias -> !alias.writeIndex()))
      .collect(toList());
  }

  private String getExpectedIndexNameBeforeRollover() {
    return embeddedOptimizeExtension.getIndexNameService()
      .getOptimizeIndexTemplateNameWithVersion(new ExternalProcessVariableIndex()) + INDEX_SUFFIX_PRE_ROLLOVER;
  }

  private String getExpectedIndexNameAfterFirstRollover() {
    return embeddedOptimizeExtension.getIndexNameService()
      .getOptimizeIndexTemplateNameWithVersion(new ExternalProcessVariableIndex()) + EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER;
  }

  private String getExpectedIndexNameAfterSecondRollover() {
    return embeddedOptimizeExtension.getIndexNameService()
      .getOptimizeIndexTemplateNameWithVersion(new ExternalProcessVariableIndex()) + EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER;
  }
}
