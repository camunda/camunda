/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.junit.jupiter.api.Test;

class ProcessVisualizationSerializationTest {

  private final ObjectMapper objectMapper = ObjectMapperFactory.OPTIMIZE_MAPPER;

  @Test
  void shouldSerializeOutlierBandToItsWireId() throws Exception {
    // given / when
    final String json = objectMapper.writeValueAsString(ProcessVisualization.OUTLIER_BAND);

    // then — the @JsonValue id is the contract shared with the frontend
    assertThat(json).isEqualTo("\"outlierBand\"");
  }

  @Test
  void shouldDeserializeOutlierBandFromItsWireId() throws Exception {
    // given / when
    final ProcessVisualization result =
        objectMapper.readValue("\"outlierBand\"", ProcessVisualization.class);

    // then
    assertThat(result).isEqualTo(ProcessVisualization.OUTLIER_BAND);
  }
}
