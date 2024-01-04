/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate311to312;

import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateSequenceCountIndicesIT extends AbstractUpgrade312IT {

  @SneakyThrows
  @Test
  public void updateDefaultTenantIdForCollectionScopesInC8_zeebeImportEnabled() {
    // given
    executeBulk("steps/3.11/sequences/311-sequence-count-data.json");

    // when
    performUpgrade();

    // then
    final SearchResponse externalSequences = prefixAwareClient.search(
      new SearchRequest(EXTERNAL_EVENT_SEQUENCE_COUNT_V3.getIndexName()));
    assertThat(
      Arrays.stream(externalSequences.getHits().getHits())
        .map(SearchHit::getSourceAsMap)
        .map(hit -> Pair.of(
               ((Map) hit.get(EventSequenceCountDto.Fields.sourceEvent)).get(EventTypeDto.Fields.eventLabel),
               ((Map) hit.get(EventSequenceCountDto.Fields.targetEvent)).get(EventTypeDto.Fields.eventLabel)
             )
        ).collect(Collectors.toList()))
      .containsExactly(Pair.of(null, null));
    final SearchResponse camundaEventSequences = prefixAwareClient.search(
      new SearchRequest(CAMUNDA_EVENT_SEQUENCE_COUNT_V3.getIndexName()));
    assertThat(
      Arrays.stream(camundaEventSequences.getHits().getHits())
        .map(SearchHit::getSourceAsMap)
        .map(hit -> Pair.of(
               ((Map) hit.get(EventSequenceCountDto.Fields.sourceEvent)).get(EventTypeDto.Fields.eventLabel),
               ((Map) hit.get(EventSequenceCountDto.Fields.targetEvent)).get(EventTypeDto.Fields.eventLabel)
             )
        ).collect(Collectors.toList()))
      .containsExactly(Pair.of(null, null));
  }

}
