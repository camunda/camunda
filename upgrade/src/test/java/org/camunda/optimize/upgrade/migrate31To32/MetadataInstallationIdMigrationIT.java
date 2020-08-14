/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32.FROM_VERSION;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

public class MetadataInstallationIdMigrationIT extends AbstractUpgrade31IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void installationIdExistsAfterUpgrade() {
    // given
    addMetadataWithMissingInstallationId();
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Optional<MetadataDto> metadataDto = metadataService.readMetadata(prefixAwareClient);
    assertThat(metadataDto)
      .isPresent()
      .get()
      .extracting(MetadataDto::getInstallationId)
      .isNotNull();
  }

  @SneakyThrows
  private void addMetadataWithMissingInstallationId() {
    MetadataDto metadataDto = new MetadataDto(FROM_VERSION, null);
    final String source = objectMapper.writeValueAsString(metadataDto);
    final IndexRequest request = new IndexRequest(METADATA_INDEX_NAME)
      .id("1")
      .source(source, XContentType.JSON)
      .setRefreshPolicy(IMMEDIATE);
    prefixAwareClient.index(request, RequestOptions.DEFAULT);
  }
}
