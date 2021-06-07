/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.plugin.elasticsearch.CustomHeader;
import org.camunda.optimize.plugin.elasticsearch.ElasticsearchCustomHeaderSupplier;
import org.elasticsearch.client.RequestOptions;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RequestOptionsProvider {

  private List<Supplier<CustomHeader>> customHeaderSuppliers;

  public RequestOptionsProvider() {
    this(Collections.emptyList());
  }

  public RequestOptionsProvider(final List<ElasticsearchCustomHeaderSupplier> customHeaderPlugins) {
    this.customHeaderSuppliers = customHeaderPlugins
      .stream()
      .map(plugin -> (Supplier<CustomHeader>) (plugin::getElasticsearchCustomHeader))
      .collect(Collectors.toList());
  }

  public RequestOptions getRequestOptions() {
    if (!customHeaderSuppliers.isEmpty()) {
      final RequestOptions.Builder requestOptionsBuilder = RequestOptions.DEFAULT.toBuilder();
      customHeaderSuppliers.forEach(headerFunction -> {
        final CustomHeader customHeader = headerFunction.get();
        requestOptionsBuilder.addHeader(customHeader.getHeader(), customHeader.getValue());
      });
      return requestOptionsBuilder.build();
    } else {
      return RequestOptions.DEFAULT;
    }
  }

}
