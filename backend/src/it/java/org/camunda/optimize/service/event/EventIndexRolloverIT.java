/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.event;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.events.rollover.EventIndexRolloverService;
import org.camunda.optimize.service.util.configuration.EventIndexRolloverConfiguration;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class EventIndexRolloverIT extends AbstractIT {
  private static final int EXPECTED_NUMBER_OF_EVENTS = 10;
  private static final String EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER = "-000002";
  private static final String EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER = "-000003";

  @Test
  public void testRolloverDueToDocAmountSuccessful() {
    // given
    addEvents();
    getEventIndexRolloverConfiguration().setMaxDocs(EXPECTED_NUMBER_OF_EVENTS);

    // when
    final boolean isRolledOver = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(isRolledOver).isTrue();
  }

  @Test
  public void testRolloverDueToAgeSuccessful() {
    // given
    addEvents();
    getEventIndexRolloverConfiguration().setMaxAgeInDays(0);

    // when
    final boolean isRolledOver = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(isRolledOver).isTrue();
  }

  @SneakyThrows
  @Test
  public void testMultipleRolloversSuccessful() {
    // given
    getEventIndexRolloverConfiguration().setMaxAgeInDays(0);

    // when
    addEvents();
    final boolean isRolledOver1 = getEventIndexRolloverService().triggerRollover();
    addEvents();
    final boolean isRolledOver2 = getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithEventWriteAlias = getAllIndicesWithEventWriteAlias();
    final int eventCount = eventClient.getAllStoredEvents().size();

    // then
    assertThat(isRolledOver1).isTrue();
    assertThat(isRolledOver2).isTrue();
    assertThat(indicesWithEventWriteAlias.size()).isEqualTo(1);
    assertThat(indicesWithEventWriteAlias.get(0).contains(EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER));
    assertThat(eventCount).isEqualTo(EXPECTED_NUMBER_OF_EVENTS * 2);
  }

  @Test
  public void testRolloverUnsuccessful() {
    // given
    addEvents();

    // when
    final boolean isRolledOver = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(isRolledOver).isFalse();
  }

  @Test
  @SneakyThrows
  public void aliasAssociatedWithCorrectIndexAfterRollover() {
    // given
    addEvents();
    getEventIndexRolloverConfiguration().setMaxDocs(EXPECTED_NUMBER_OF_EVENTS);

    // when
    getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithEventWriteAlias = getAllIndicesWithEventWriteAlias();

    // then
    assertThat(indicesWithEventWriteAlias.size()).isEqualTo(1);
    assertThat(indicesWithEventWriteAlias.get(0).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
  }

  @Test
  public void noDataLossAfterRollover() {
    // given
    addEvents();
    getEventIndexRolloverConfiguration().setMaxDocs(EXPECTED_NUMBER_OF_EVENTS);

    // when
    getEventIndexRolloverService().triggerRollover();

    // then
    final int eventCount = eventClient.getAllStoredEvents().size();
    assertThat(eventCount).isEqualTo(EXPECTED_NUMBER_OF_EVENTS);
  }

  @Test
  public void searchAliasPointsToAllIndicesAfterRollover() {
    // given
    addEvents();
    getEventIndexRolloverConfiguration().setMaxDocs(EXPECTED_NUMBER_OF_EVENTS);

    // when
    getEventIndexRolloverService().triggerRollover();
    addEvents();

    // then there are 2 * EXPECTED_NUMBER_OF_EVENTS present (half in the old index and half in the new)
    final int eventCount = eventClient.getAllStoredEvents().size();
    assertThat(eventCount).isEqualTo(EXPECTED_NUMBER_OF_EVENTS * 2);
  }

  private EventIndexRolloverService getEventIndexRolloverService() {
    return embeddedOptimizeExtension.getEventIndexRolloverService();
  }

  private EventIndexRolloverConfiguration getEventIndexRolloverConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration();
  }

  private String getApiSecret() {
    return embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().getApiSecret();
  }

  private void addEvents() {
    final List<CloudEventDto> eventDtos = IntStream.range(0, EXPECTED_NUMBER_OF_EVENTS)
      .mapToObj(operand -> eventClient.createCloudEventDto())
      .collect(toList());

    embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(eventDtos, getApiSecret())
      .execute();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private List<String> getAllIndicesWithEventWriteAlias() throws IOException {
    final String eventAliasNameWithPrefix = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(
        ElasticsearchConstants.EVENT_INDEX_NAME);

    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(eventAliasNameWithPrefix);
    Map<String, Set<AliasMetaData>> aliasMap = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getHighLevelClient()
      .indices()
      .getAlias(aliasesRequest, RequestOptions.DEFAULT)
      .getAliases();

    return aliasMap.keySet()
      .stream()
      .filter(index -> aliasMap.get(index).removeIf(AliasMetaData::writeIndex))
      .collect(toList());
  }

  @AfterEach
  private void cleanUpEventIndices() {
    elasticSearchIntegrationTestExtension.deleteAllEventIndices();

    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new EventIndex()
    );
  }
}
