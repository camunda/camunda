/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PhysicalTenantPartitionListenerFactory;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Gives every physical tenant its own Hub fan-out puller, bound to its partition-1 leadership. */
@Component
@EnableConfigurationProperties(HubFanoutProperties.class)
@ConditionalOnProperty(prefix = "camunda.hub.fanout", name = "enabled", havingValue = "true")
public class HubFanoutPartitionListenerFactory implements PhysicalTenantPartitionListenerFactory {

  private final HubFanoutProperties properties;
  private final Map<String, FanoutEventHandler> handlers;
  private final HttpClient http = HttpClient.newHttpClient();
  private final List<HubFanoutPartitionListener> listeners = new CopyOnWriteArrayList<>();

  public HubFanoutPartitionListenerFactory(
      final HubFanoutProperties properties, final List<FanoutEventHandler> handlers) {
    this.properties = properties;
    this.handlers =
        handlers.stream().collect(Collectors.toMap(FanoutEventHandler::type, Function.identity()));
  }

  @Override
  public List<PartitionListener> create(final String physicalTenantId) {
    final var listener =
        new HubFanoutPartitionListener(
            () -> new HubFanoutPuller(physicalTenantId, properties, handlers, http));
    listeners.add(listener);
    return List.of(listener);
  }

  @PreDestroy
  void stopAll() {
    listeners.forEach(HubFanoutPartitionListener::stop);
  }
}
