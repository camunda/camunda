/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.plugin.elasticsearch.CustomHeader;
import org.camunda.optimize.plugin.elasticsearch.ElasticsearchCustomHeaderSupplier;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RequestOptionsProvider {

  private final List<Supplier<CustomHeader>> customHeaderSuppliers;
  private final ConfigurationService configurationService;

  public RequestOptionsProvider() {
    this(Collections.emptyList(), null);
  }

  public RequestOptionsProvider(final List<ElasticsearchCustomHeaderSupplier> customHeaderPlugins,
                                final ConfigurationService configurationService) {
    this.customHeaderSuppliers = customHeaderPlugins
      .stream()
      .map(plugin -> (Supplier<CustomHeader>) (plugin::getElasticsearchCustomHeader))
      .collect(Collectors.toList());
    this.configurationService = configurationService;
  }

  public RequestOptions getRequestOptions() {
    final RequestOptions.Builder optionsBuilder = RequestOptions.DEFAULT.toBuilder();
    Optional.ofNullable(configurationService).ifPresent(config -> optionsBuilder.setHttpAsyncResponseConsumerFactory(
      new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(
        getElasticsearchResponseConsumerBufferLimitInBytes())));
    if (!customHeaderSuppliers.isEmpty()) {
      customHeaderSuppliers.forEach(headerFunction -> {
        final CustomHeader customHeader = headerFunction.get();
        optionsBuilder.addHeader(customHeader.getHeader(), customHeader.getValue());
      });
    }
    return optionsBuilder.build();
  }

  private int getElasticsearchResponseConsumerBufferLimitInBytes() {
    return configurationService.getElasticsearchResponseConsumerBufferLimitInMb() * 1024 * 1024;
  }

}
