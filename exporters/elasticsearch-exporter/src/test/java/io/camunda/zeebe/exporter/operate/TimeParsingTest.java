/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.IncidentEntity;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class TimeParsingTest {

  @Test
  public void shouldFormatOffsetDateTimeCorrectly() throws JsonProcessingException {

    // given
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
    final ObjectMapper objectMapper = NoSpringJacksonConfig.buildObjectMapper();

    final OffsetDateTime dateTime = OffsetDateTime.parse("2023-12-12T15:49:07.374+0000", formatter);

    final IncidentEntity entity = new IncidentEntity();
    entity.setCreationTime(dateTime);
    entity.setErrorMessage("Foo");

    // when
    final String json = objectMapper.writeValueAsString(entity);

    // then
    final JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();

    final OffsetDateTime parsedDateTime =
        OffsetDateTime.parse(jsonObject.getString("creationTime"), formatter);
    assertThat(parsedDateTime).isEqualTo(dateTime);
  }
}
