/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.exceptions.OptimizeMetadataReadException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticSearchMetadataServiceTest {

  @Mock private OptimizeElasticsearchClient esClient;

  @Test
  void shouldReportUnavailableDatabaseAsMetadataReadFailure() throws IOException {
    // given
    final IOException unavailable = new IOException("Connection refused");
    given(esClient.exists(METADATA_INDEX_NAME)).willThrow(unavailable);
    final ElasticSearchMetadataService underTest =
        new ElasticSearchMetadataService(new ObjectMapper());

    // when / then
    assertThatThrownBy(() -> underTest.readMetadata(esClient))
        .isInstanceOf(OptimizeMetadataReadException.class)
        .hasCause(unavailable);
  }
}
