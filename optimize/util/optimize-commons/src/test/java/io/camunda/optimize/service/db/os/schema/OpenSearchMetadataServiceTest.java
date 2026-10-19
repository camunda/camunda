/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.exceptions.OptimizeMetadataReadException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenSearchMetadataServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OptimizeOpenSearchClient osClient;

  @Test
  void shouldReportUnavailableDatabaseAsMetadataReadFailure() {
    // given
    final OptimizeRuntimeException unavailable =
        new OptimizeRuntimeException("Failed to check if index exists");
    given(osClient.getRichOpenSearchClient().index().indexExists(METADATA_INDEX_NAME))
        .willThrow(unavailable);
    final OpenSearchMetadataService underTest = new OpenSearchMetadataService(new ObjectMapper());

    // when / then
    assertThatThrownBy(() -> underTest.readMetadata(osClient))
        .isInstanceOf(OptimizeMetadataReadException.class)
        .hasCause(unavailable);
  }
}
