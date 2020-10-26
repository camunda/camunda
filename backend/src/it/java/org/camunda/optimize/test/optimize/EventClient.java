/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.util.IdGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public class EventClient {
  private static final Random RANDOM = new Random();

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;
  private final Supplier<String> accessTokenSupplier;

  public void ingestEventBatch(final List<CloudEventRequestDto> eventDtos) {
    requestExecutorSupplier.get().buildIngestEventBatch(eventDtos, accessTokenSupplier.get()).execute();
  }

  public List<CloudEventRequestDto> ingestEventBatchWithTimestamp(final Instant timestamp, final int eventCount) {
    final List<CloudEventRequestDto> ingestedEvents = IntStream.range(0, eventCount)
      .mapToObj(operand -> createCloudEventDto().toBuilder().time(timestamp).build())
      .collect(toList());
    ingestEventBatch(ingestedEvents);
    return ingestedEvents;
  }

  public CloudEventRequestDto createCloudEventDto() {
    return CloudEventRequestDto.builder()
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
