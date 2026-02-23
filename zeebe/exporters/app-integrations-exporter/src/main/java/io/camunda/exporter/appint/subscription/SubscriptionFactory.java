/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.subscription;

import static io.camunda.exporter.appint.transport.Authentication.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.exporter.appint.config.BatchConfig;
import io.camunda.exporter.appint.config.Config;
import io.camunda.exporter.appint.config.ConfigValidator;
import io.camunda.exporter.appint.event.Event;
import io.camunda.exporter.appint.mapper.SupportedRecordsMapper;
import io.camunda.exporter.appint.transport.Authentication.ApiKey;
import io.camunda.exporter.appint.transport.HttpTransportConfig;
import io.camunda.exporter.appint.transport.HttpTransportImpl;
import io.camunda.exporter.appint.transport.JsonMapper;
import java.util.function.Consumer;

public class SubscriptionFactory {

  public static JsonMapper createJsonMapper() {
    final var objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return (object) -> {
      try {
        return objectMapper.writeValueAsString(object);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    };
  }

  public static Subscription<Event> createDefault(
      final Config config, final Consumer<Long> positionConsumer) {
    ConfigValidator.validate(config);
    final var auth =
        switch (config.getApiKey()) {
          case null -> None.INSTANCE;
          default -> new ApiKey(config.getApiKey());
        };
    final var httpTransportConfig =
        new HttpTransportConfig(
            config.getUrl(),
            auth,
            config.getMaxRetries(),
            config.getRetryDelayMs(),
            config.getRequestTimeoutMs());
    final var transport = new HttpTransportImpl(createJsonMapper(), httpTransportConfig);
    final var mapper = new SupportedRecordsMapper();
    final var batchConfig =
        new BatchConfig(
            config.getMaxBatchesInFlight(),
            config.getBatchSize(),
            config.getBatchIntervalMs(),
            config.isContinueOnError());
    return new Subscription<>(transport, mapper, batchConfig, positionConsumer);
  }
}
