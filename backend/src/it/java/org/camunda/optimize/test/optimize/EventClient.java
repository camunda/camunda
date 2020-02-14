/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@AllArgsConstructor
public class EventClient {

  private static final Random RANDOM = new Random();

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;

  public void ingestEventBatch(final List<CloudEventDto> eventDtos) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(
        eventDtos,
        embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().getAccessToken()
      )
      .execute();
  }

  public CloudEventDto createCloudEventDto() {
    return CloudEventDto.builder()
      .id(IdGenerator.getNextId())
      .source(RandomStringUtils.randomAlphabetic(10))
      .specversion("1.0")
      .type(RandomStringUtils.randomAlphabetic(10))
      .time(Instant.now())
      .group(RandomStringUtils.randomAlphabetic(10))
      .data(
        ImmutableMap.of(
          RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
          RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
          RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
        )
      )
      .traceid(RandomStringUtils.randomAlphabetic(10))
      .build();
  }

}
