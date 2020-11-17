/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom32To33;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EventIndexSettingsMigrationIT extends AbstractUpgrade32IT {

  // @formatter:off
  private static final ImmutableMap<String, Object> EXPECTED_NEW_MAPPING_PROPERTIES =
    ImmutableMap.of("type", "keyword",
                    "fields", ImmutableMap.of(
                      "nGramField", ImmutableMap.of(
                        "analyzer", "lowercase_ngram",
                        "type", "text"
                        ),
                      "lowercase", ImmutableMap.of(
                        "normalizer", "lowercase_normalizer",
                        "type", "keyword"
                        )
                      )
                    );
  // @formatter:on
  private static final ImmutableMap<String, Object> OLD_MAPPING_PROPERTIES = ImmutableMap.of("type", "keyword");

  @Test
  public void externalEventIndexHasSettingsUpdated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // then
    assertThat(getMappingsForEventIndex().mappings())
      .hasSize(1)
      .satisfies(mappings -> {
        assertThat(extractMappingProperties(mappings))
          .containsEntry(EventIndex.EVENT_NAME, OLD_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.GROUP, OLD_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.SOURCE, OLD_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.TRACE_ID, OLD_MAPPING_PROPERTIES);
      });

    // when
    upgradePlan.execute();

    // then
    assertThat(getMappingsForEventIndex().mappings())
      .hasSize(1)
      .satisfies(mappings -> {
        assertThat(extractMappingProperties(mappings))
          .containsEntry(EventIndex.EVENT_NAME, EXPECTED_NEW_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.GROUP, EXPECTED_NEW_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.SOURCE, EXPECTED_NEW_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.TRACE_ID, EXPECTED_NEW_MAPPING_PROPERTIES);
      });
  }

  @Test
  public void allRolledOverExternalEventIndicesHaveSettingsUpdated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();
    configurationService.getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    triggerEventIndexRollover();
    triggerEventIndexRollover();

    // then all three indices have the old settings
    assertThat(getMappingsForEventIndex().mappings())
      .hasSize(3)
      .satisfies(mappings -> {
        assertThat(extractMappingProperties(mappings))
          .containsEntry(EventIndex.EVENT_NAME, OLD_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.GROUP, OLD_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.SOURCE, OLD_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.TRACE_ID, OLD_MAPPING_PROPERTIES);
      });

    // when
    upgradePlan.execute();

    // then all three indices have the new settings
    assertThat(getMappingsForEventIndex().mappings())
      .hasSize(3)
      .satisfies(mappings -> {
        assertThat(extractMappingProperties(mappings))
          .containsEntry(EventIndex.EVENT_NAME, EXPECTED_NEW_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.GROUP, EXPECTED_NEW_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.SOURCE, EXPECTED_NEW_MAPPING_PROPERTIES)
          .containsEntry(EventIndex.TRACE_ID, EXPECTED_NEW_MAPPING_PROPERTIES);
      });
  }

  private void triggerEventIndexRollover() {
    ElasticsearchWriterUtil.triggerRollover(
      prefixAwareClient,
      indexNameService.getOptimizeIndexAliasForIndex(EVENT_INDEX.getIndexName()),
      0
    );
  }

  @SneakyThrows
  private GetMappingsResponse getMappingsForEventIndex() {
    return prefixAwareClient.getHighLevelClient()
      .indices()
      .getMapping(
        new GetMappingsRequest().indices(indexNameService.getOptimizeIndexAliasForIndex(EVENT_INDEX.getIndexName())),
        RequestOptions.DEFAULT
      );
  }

  @SuppressWarnings("unchecked")
  private Map<String, Map<String, Object>> extractMappingProperties(final Map<String, MappingMetadata> mappings) {
    return (Map<String, Map<String, Object>>) mappings.values()
      .iterator()
      .next()
      .getSourceAsMap()
      .get("properties");
  }

}
