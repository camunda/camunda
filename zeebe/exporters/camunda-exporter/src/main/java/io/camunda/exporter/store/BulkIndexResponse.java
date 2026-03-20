/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record BulkIndexResponse(boolean errors, List<Item> items) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Item(ItemDetail index, ItemDetail update, ItemDetail delete, ItemDetail create) {
    ItemDetail detail() {
      if (index != null) return index;
      if (update != null) return update;
      if (delete != null) return delete;
      return create;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ItemDetail(int status, String _index, String _id, ErrorDetail error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ErrorDetail(String type, String reason) {}
}
