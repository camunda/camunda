package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record JobFilter(
    List<Operation<String>> stateOperations,
    List<Operation<String>> typeOperations,
    List<Operation<String>> workerOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<String>> elementIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<JobFilter> {
    private List<Operation<String>> stateOperations;
    private List<Operation<String>> typeOperations;
    private List<Operation<String>> workerOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> elementInstanceKeyOperations;
    private List<Operation<String>> elementIdOperation;

    public Builder stateOperations(final List<Operation<String>> values) {
      stateOperations = addValuesToList(stateOperations, values);
      return this;
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    public Builder typeOperations(final List<Operation<String>> operations) {
      typeOperations = addValuesToList(typeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder typeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return typeOperations(collectValues(operation, operations));
    }

    public Builder types(final String value, final String... values) {
      return typeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder workerOperations(final List<Operation<String>> operations) {
      workerOperations = addValuesToList(workerOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder workerOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return workerOperations(collectValues(operation, operations));
    }

    public Builder workers(final String value, final String... values) {
      return workerOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder elementInstanceKeyOperations(final List<Operation<Long>> operations) {
      elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder elementInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return elementInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder elementInstanceKeys(final Long value, final Long... values) {
      return elementInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder elementIdOperations(final List<Operation<String>> operations) {
      elementIdOperation = addValuesToList(elementIdOperation, operations);
      return this;
    }

    @SafeVarargs
    public final Builder elementIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return elementIdOperations(collectValues(operation, operations));
    }

    public Builder elementIds(final String value, final String... values) {
      return elementIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @Override
    public JobFilter build() {
      return new JobFilter(
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(typeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(workerOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementIdOperation, Collections.emptyList()));
    }
  }
}
