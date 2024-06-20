/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ProcessDefinitionFilter(
    List<Long> keys,
    List<String> names,
    List<Integer> versions,
    List<String> bpmnProcessIds,
    List<Long> formKeys)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessDefinitionFilter> {

    private List<Long> keys;
    private List<String> names;
    private List<Integer> versions;
    private List<String> bpmnProcessIds;
    private List<Long> formKeys;

    public Builder keys(final Long value, final Long... values) {
      return keys(collectValues(value, values));
    }

    public Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    public Builder names(final List<String> values) {
      names = addValuesToList(names, values);
      return this;
    }

    public Builder names(final String value, final String... values) {
      return names(collectValues(value, values));
    }

    public Builder versions(final List<Integer> values) {
      versions = addValuesToList(versions, values);
      return this;
    }

    public Builder versions(final Integer value, final Integer... values) {
      return versions(collectValues(value, values));
    }

    public Builder bpmnProcessIds(final List<String> values) {
      bpmnProcessIds = addValuesToList(bpmnProcessIds, values);
      return this;
    }

    public Builder bpmnProcessIds(final String value, final String... values) {
      return bpmnProcessIds(collectValues(value, values));
    }

    public Builder formKeys(final Long value, final Long... values) {
      return formKeys(collectValues(value, values));
    }

    public Builder formKeys(final List<Long> values) {
      formKeys = addValuesToList(formKeys, values);
      return this;
    }

    @Override
    public ProcessDefinitionFilter build() {
      return new ProcessDefinitionFilter(
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(names, Collections.emptyList()),
          Objects.requireNonNullElse(versions, Collections.emptyList()),
          Objects.requireNonNullElse(bpmnProcessIds, Collections.emptyList()),
          Objects.requireNonNullElse(formKeys, Collections.emptyList()));
    }
  }
}
