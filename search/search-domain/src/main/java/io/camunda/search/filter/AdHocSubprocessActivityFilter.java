/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record AdHocSubprocessActivityFilter(
    List<String> adHocSubprocessInstanceKeys,
    List<String> adHocSubprocessIds,
    List<String> processInstanceKeys,
    List<String> processDefinitionKeys,
    List<String> processDefinitionIds,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<AdHocSubprocessActivityFilter> {

    private List<String> adHocSubprocessInstanceKeys;
    private List<String> adHocSubprocessIds;
    private List<String> processInstanceKeys;
    private List<String> processDefinitionKeys;
    private List<String> processDefinitionIds;
    private List<String> tenantIds;

    public AdHocSubprocessActivityFilter.Builder adHocSubprocessInstanceKeys(
        final List<String> values) {
      adHocSubprocessInstanceKeys = addValuesToList(adHocSubprocessInstanceKeys, values);
      return this;
    }

    public AdHocSubprocessActivityFilter.Builder adHocSubprocessInstanceKeys(
        final String... values) {
      return adHocSubprocessInstanceKeys(collectValuesAsList(values));
    }

    public AdHocSubprocessActivityFilter.Builder adHocSubprocessIds(final List<String> values) {
      adHocSubprocessIds = addValuesToList(adHocSubprocessIds, values);
      return this;
    }

    public AdHocSubprocessActivityFilter.Builder adHocSubprocessIds(final String... values) {
      return adHocSubprocessIds(collectValuesAsList(values));
    }

    public AdHocSubprocessActivityFilter.Builder processInstanceKeys(final List<String> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public AdHocSubprocessActivityFilter.Builder processInstanceKeys(final String... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public AdHocSubprocessActivityFilter.Builder processDefinitionKeys(final List<String> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public AdHocSubprocessActivityFilter.Builder processDefinitionKeys(final String... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public AdHocSubprocessActivityFilter.Builder processDefinitionIds(final List<String> values) {
      processDefinitionIds = addValuesToList(processDefinitionIds, values);
      return this;
    }

    public AdHocSubprocessActivityFilter.Builder processDefinitionIds(final String... values) {
      return processDefinitionIds(collectValuesAsList(values));
    }

    public AdHocSubprocessActivityFilter.Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public AdHocSubprocessActivityFilter.Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public AdHocSubprocessActivityFilter build() {
      return new AdHocSubprocessActivityFilter(
          Objects.requireNonNullElse(adHocSubprocessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(adHocSubprocessIds, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
