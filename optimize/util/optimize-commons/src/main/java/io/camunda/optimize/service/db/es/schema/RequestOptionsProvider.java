/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import io.camunda.optimize.plugin.elasticsearch.CustomHeader;
import io.camunda.optimize.plugin.elasticsearch.DatabaseCustomHeaderSupplier;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;

public class RequestOptionsProvider {

  private final List<Supplier<CustomHeader>> customHeaderSuppliers;
  private final ConfigurationService configurationService;

  public RequestOptionsProvider() {
    this(Collections.emptyList(), null);
  }

  public RequestOptionsProvider(
      final List<DatabaseCustomHeaderSupplier> customHeaderPlugins,
      final ConfigurationService configurationService) {
    this.customHeaderSuppliers =
        customHeaderPlugins.stream()
            .map(plugin -> (Supplier<CustomHeader>) (plugin::getElasticsearchCustomHeader))
            .collect(Collectors.toList());
    this.configurationService = configurationService;
  }

  public RequestOptions getRequestOptions() {
    final RequestOptions.Builder optionsBuilder = RequestOptions.DEFAULT.toBuilder();
    Optional.ofNullable(configurationService)
        .ifPresent(
            config ->
                optionsBuilder.setHttpAsyncResponseConsumerFactory(
                    new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(
                        getElasticsearchResponseConsumerBufferLimitInBytes())));
    if (!customHeaderSuppliers.isEmpty()) {
      customHeaderSuppliers.forEach(
          headerFunction -> {
            final CustomHeader customHeader = headerFunction.get();
            optionsBuilder.addHeader(customHeader.getHeader(), customHeader.getValue());
          });
    }
    return optionsBuilder.build();
  }

  private int getElasticsearchResponseConsumerBufferLimitInBytes() {
    return configurationService.getElasticSearchConfiguration().getResponseConsumerBufferLimitInMb()
        * 1024
        * 1024;
  }
}
