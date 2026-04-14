/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ResourceFilter(List<Long> resourceKeys, List<String> resourceIds, String tenantId)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ResourceFilter> {

    private List<Long> resourceKeys;
    private List<String> resourceIds;
    private String tenantId;

    public Builder resourceKeys(final Long value, final Long... values) {
      return resourceKeys(collectValues(value, values));
    }

    public Builder resourceKeys(final List<Long> values) {
      resourceKeys = addValuesToList(resourceKeys, values);
      return this;
    }

    public Builder resourceIds(final String value, final String... values) {
      return resourceIds(collectValues(value, values));
    }

    public Builder resourceIds(final List<String> values) {
      resourceIds = addValuesToList(resourceIds, values);
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    @Override
    public ResourceFilter build() {
      return new ResourceFilter(
          Objects.requireNonNullElse(resourceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(resourceIds, Collections.emptyList()),
          tenantId);
    }
  }
}
