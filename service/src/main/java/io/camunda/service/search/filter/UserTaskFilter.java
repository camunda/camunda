package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.service.query.filter.FieldFilter;
import io.camunda.service.query.filter.FilterOperator;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record UserTaskFilter(
    FieldFilter<List<Long>> keys,
    List<String> elementIds,
    List<String> bpmnProcessIds,
    List<String> assignees,
    FieldFilter<Object> states,  // Changed to FieldFilter for states
    List<Long> processInstanceKeys,
    List<Long> processDefinitionKeys,
    List<String> candidateUsers,
    List<String> candidateGroups,
    List<String> tenantIds,
    ComparableValueFilter priority)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<UserTaskFilter> {
    private FieldFilter<List<Long>> keys;
    private List<String> elementIds;
    private List<String> bpmnProcessIds;
    private List<String> assignees;
    private FieldFilter<Object> states;  // FieldFilter for states
    private List<Long> processInstanceKeys;
    private List<Long> processDefinitionKeys;
    private List<String> candidateUsers;
    private List<String> candidateGroups;
    private List<String> tenantIds;
    private ComparableValueFilter priority;

    // Builder for keys (FieldFilter<List<Long>>)
    public Builder keys(final FilterOperator operator, final List<Long> values) {
      keys = new FieldFilter<>(operator, values);  // Assigning operator and values to FieldFilter for keys
      return this;
    }

    public Builder keys(final FilterOperator operator, final Long... values) {
      keys = new FieldFilter<>(operator, collectValuesAsList(values));  // Assigning operator and values to FieldFilter for keys
      return this;
    }

    // Builder for states (FieldFilter<List<String>>)
    public Builder states(final FilterOperator operator, final Object values) {
      states = new FieldFilter<>(operator, values);  // Assigning operator and values to FieldFilter for states
      return this;
    }

    // Other builder methods remain the same
    public Builder elementIds(final String... values) {
      return elementIds(collectValuesAsList(values));
    }

    public Builder elementIds(final List<String> values) {
      elementIds = addValuesToList(elementIds, values);
      return this;
    }

    public Builder bpmnProcessIds(final String... values) {
      return bpmnProcessIds(collectValuesAsList(values));
    }

    public Builder bpmnProcessIds(final List<String> values) {
      bpmnProcessIds = addValuesToList(bpmnProcessIds, values);
      return this;
    }

    public Builder assignees(final String... values) {
      return assignees((collectValuesAsList(values)));
    }

    public Builder assignees(final List<String> values) {
      assignees = addValuesToList(assignees, values);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder candidateUsers(final String... values) {
      return candidateUsers(collectValuesAsList(values));
    }

    public Builder candidateUsers(final List<String> values) {
      candidateUsers = addValuesToList(candidateUsers, values);
      return this;
    }

    public Builder candidateGroups(final String... values) {
      return candidateGroups(collectValuesAsList(values));
    }

    public Builder candidateGroups(final List<String> values) {
      candidateGroups = addValuesToList(candidateGroups, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder priority(final ComparableValueFilter value) {
      priority = value;
      return this;
    }

    @Override
    public UserTaskFilter build() {
      return new UserTaskFilter(
          Objects.requireNonNullElse(keys, new FieldFilter<>(FilterOperator.EQ, Collections.emptyList())),  // Default FieldFilter for keys
          Objects.requireNonNullElse(elementIds, Collections.emptyList()),
          Objects.requireNonNullElse(bpmnProcessIds, Collections.emptyList()),
          Objects.requireNonNullElse(assignees, Collections.emptyList()),
          Objects.requireNonNullElse(states, new FieldFilter<>(FilterOperator.EQ, Collections.emptyList())),  // Default FieldFilter for states
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(candidateUsers, Collections.emptyList()),
          Objects.requireNonNullElse(candidateGroups, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()),
          priority);
    }
  }
}
