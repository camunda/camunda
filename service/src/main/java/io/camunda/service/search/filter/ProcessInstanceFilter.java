/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.*;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceFilter(
    boolean running,
    boolean active,
    boolean incidents,
    boolean finished,
    boolean completed,
    boolean canceled,
    boolean retriesLeft,
    String errorMessage,
    String activityId,
    DateValueFilter startDate,
    DateValueFilter endDate,
    List<String> bpmnProcessIds,
    List<Integer> processDefinitionVersions,
    ProcessInstanceVariableFilter variable,
    List<String> batchOperationIds,
    List<Long> parentProcessInstanceKeys,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private boolean running;
    private boolean active;
    private boolean incidents;
    private boolean finished;
    private boolean completed;
    private boolean canceled;
    private boolean retriesLeft;
    private String errorMessage;
    private String activityId;
    private DateValueFilter startDate;
    private DateValueFilter endDate;
    private List<String> bpmnProcessIds;
    private List<Integer> processDefinitionVersions;
    private ProcessInstanceVariableFilter variable;
    private List<String> batchOperationIds;
    private List<Long> parentProcessInstanceKeys;
    private List<String> tenantIds;

    public Builder running(final boolean running) {
      this.running = running;
      return this;
    }

    public Builder active(final boolean active) {
      this.active = active;
      return this;
    }

    public Builder incidents(final boolean incidents) {
      this.incidents = incidents;
      return this;
    }

    public Builder finished(final boolean finished) {
      this.finished = finished;
      return this;
    }

    public Builder completed(final boolean completed) {
      this.completed = completed;
      return this;
    }

    public Builder canceled(final boolean canceled) {
      this.canceled = canceled;
      return this;
    }

    public Builder retriesLeft(final boolean retriesLeft) {
      this.retriesLeft = retriesLeft;
      return this;
    }

    public Builder errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder activityId(final String activityId) {
      this.activityId = activityId;
      return this;
    }

    public Builder startDate(final DateValueFilter startDate) {
      this.startDate = startDate;
      return this;
    }

    public Builder startDate(
        final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
      return startDate(FilterBuilders.dateValue(fn));
    }

    public Builder endDate(final DateValueFilter endDate) {
      this.endDate = endDate;
      return this;
    }

    public Builder endDate(
        final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
      return endDate(FilterBuilders.dateValue(fn));
    }

    public Builder bpmnProcessIds(final List<String> values) {
      bpmnProcessIds = addValuesToList(bpmnProcessIds, values);
      return this;
    }

    public Builder bpmnProcessIds(final String... values) {
      return bpmnProcessIds(collectValuesAsList(values));
    }

    public Builder processDefinitionVersions(final List<Integer> values) {
      processDefinitionVersions = addValuesToList(processDefinitionVersions, values);
      return this;
    }

    public Builder processDefinitionVersions(final Integer... values) {
      return processDefinitionVersions(collectValuesAsList(values));
    }

    public Builder variable(final ProcessInstanceVariableFilter variable) {
      this.variable = variable;
      return this;
    }

    public Builder variable(
        final Function<
                ProcessInstanceVariableFilter.Builder, ObjectBuilder<ProcessInstanceVariableFilter>>
            fn) {
      return variable(FilterBuilders.processInstanceVariable(fn));
    }

    public Builder batchOperationIds(final List<String> values) {
      batchOperationIds = addValuesToList(batchOperationIds, values);
      return this;
    }

    public Builder batchOperationIds(final String... values) {
      return batchOperationIds(collectValuesAsList(values));
    }

    public Builder parentProcessInstanceKeys(final List<Long> values) {
      parentProcessInstanceKeys = addValuesToList(parentProcessInstanceKeys, values);
      return this;
    }

    public Builder parentProcessInstanceKeys(final Long... values) {
      return parentProcessInstanceKeys(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public ProcessInstanceFilter build() {
      return new ProcessInstanceFilter(
          running,
          active,
          incidents,
          finished,
          completed,
          canceled,
          retriesLeft,
          errorMessage,
          activityId,
          startDate,
          endDate,
          Objects.requireNonNullElse(bpmnProcessIds, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersions, Collections.emptyList()),
          variable,
          Objects.requireNonNullElse(batchOperationIds, Collections.emptyList()),
          Objects.requireNonNullElse(parentProcessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}
