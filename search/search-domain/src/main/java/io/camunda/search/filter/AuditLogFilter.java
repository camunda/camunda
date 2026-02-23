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

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AuditLogFilter(
    List<Operation<String>> auditLogKeyOperations,
    List<Operation<String>> entityKeyOperations,
    List<Operation<String>> entityTypeOperations,
    List<Operation<String>> operationTypeOperations,
    List<Operation<String>> batchOperationTypeOperations,
    List<Operation<OffsetDateTime>> timestampOperations,
    List<Operation<String>> actorTypeOperations,
    List<Operation<String>> actorIdOperations,
    List<Operation<String>> agentElementIdOperations,
    List<Operation<String>> tenantIdOperations,
    List<Operation<String>> resultOperations,
    List<Operation<String>> categoryOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Long>> userTaskKeyOperations,
    List<Operation<String>> decisionRequirementsIdOperations,
    List<Operation<Long>> decisionRequirementsKeyOperations,
    List<Operation<String>> decisionDefinitionIdOperations,
    List<Operation<Long>> decisionDefinitionKeyOperations,
    List<Operation<Long>> decisionEvaluationKeyOperations,
    List<Operation<Long>> elementInstanceKeyOperations,
    List<Operation<Long>> jobKeyOperations,
    List<Operation<Long>> batchOperationKeyOperations,
    List<Operation<Long>> deploymentKeyOperations,
    List<Operation<Long>> formKeyOperations,
    List<Operation<Long>> resourceKeyOperations,
    List<Operation<String>> relatedEntityKeyOperations,
    List<Operation<String>> relatedEntityTypeOperations,
    List<Operation<String>> entityDescriptionOperations)
    implements FilterBase {

  public static AuditLogFilter of(
      final Function<AuditLogFilter.Builder, AuditLogFilter.Builder> builderFunction) {
    return builderFunction.apply(new AuditLogFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .auditLogKeyOperations(auditLogKeyOperations)
        .entityKeyOperations(entityKeyOperations)
        .entityTypeOperations(entityTypeOperations)
        .operationTypeOperations(operationTypeOperations)
        .batchOperationTypeOperations(batchOperationTypeOperations)
        .timestampOperations(timestampOperations)
        .actorTypeOperations(actorTypeOperations)
        .actorIdOperations(actorIdOperations)
        .agentElementIdOperations(agentElementIdOperations)
        .tenantIdOperations(tenantIdOperations)
        .resultOperations(resultOperations)
        .categoryOperations(categoryOperations)
        .processInstanceKeyOperations(processInstanceKeyOperations)
        .processDefinitionKeyOperations(processDefinitionKeyOperations)
        .processDefinitionIdOperations(processDefinitionIdOperations)
        .userTaskKeyOperations(userTaskKeyOperations)
        .decisionRequirementsIdOperations(decisionRequirementsIdOperations)
        .decisionRequirementsKeyOperations(decisionRequirementsKeyOperations)
        .decisionDefinitionIdOperations(decisionDefinitionIdOperations)
        .decisionDefinitionKeyOperations(decisionDefinitionKeyOperations)
        .decisionEvaluationKeyOperations(decisionEvaluationKeyOperations)
        .elementInstanceKeyOperations(elementInstanceKeyOperations)
        .jobKeyOperations(jobKeyOperations)
        .batchOperationKeyOperations(batchOperationKeyOperations)
        .deploymentKeyOperations(deploymentKeyOperations)
        .formKeyOperations(formKeyOperations)
        .resourceKeyOperations(resourceKeyOperations)
        .relatedEntityKeyOperations(relatedEntityKeyOperations)
        .relatedEntityTypeOperations(relatedEntityTypeOperations)
        .entityDescriptionOperations(entityDescriptionOperations);
  }

  public static final class Builder implements ObjectBuilder<AuditLogFilter> {
    private List<Operation<String>> auditLogKeyOperations;
    private List<Operation<String>> entityKeyOperations;
    private List<Operation<String>> entityTypeOperations;
    private List<Operation<String>> operationTypeOperations;
    private List<Operation<String>> batchOperationTypeOperations;
    private List<Operation<OffsetDateTime>> timestampOperations;
    private List<Operation<String>> actorTypeOperations;
    private List<Operation<String>> actorIdOperations;
    private List<Operation<String>> agentElementIdOperations;
    private List<Operation<String>> tenantIdOperations;
    private List<Operation<String>> resultOperations;
    private List<Operation<String>> categoryOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<Long>> userTaskKeyOperations;
    private List<Operation<String>> decisionRequirementsIdOperations;
    private List<Operation<Long>> decisionRequirementsKeyOperations;
    private List<Operation<String>> decisionDefinitionIdOperations;
    private List<Operation<Long>> decisionDefinitionKeyOperations;
    private List<Operation<Long>> decisionEvaluationKeyOperations;
    private List<Operation<Long>> elementInstanceKeyOperations;
    private List<Operation<Long>> jobKeyOperations;
    private List<Operation<Long>> batchOperationKeyOperations;
    private List<Operation<Long>> deploymentKeyOperations;
    private List<Operation<Long>> formKeyOperations;
    private List<Operation<Long>> resourceKeyOperations;
    private List<Operation<String>> entityDescriptionOperations;
    private List<Operation<String>> relatedEntityTypeOperations;
    private List<Operation<String>> relatedEntityKeyOperations;

    public Builder auditLogKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        auditLogKeyOperations = addValuesToList(auditLogKeyOperations, operations);
      }
      return this;
    }

    public Builder auditLogKeys(final String value, final String... values) {
      return auditLogKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder entityKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        entityKeyOperations = addValuesToList(entityKeyOperations, operations);
      }
      return this;
    }

    @SafeVarargs
    public final Builder entityKeyOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return entityKeyOperations(collectValues(operation, operations));
    }

    public Builder entityKeys(final String value, final String... values) {
      return entityKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder entityTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        entityTypeOperations = addValuesToList(entityTypeOperations, operations);
      }
      return this;
    }

    public Builder entityTypes(final String value, final String... values) {
      return entityTypeOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder operationTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        operationTypeOperations = addValuesToList(operationTypeOperations, operations);
      }
      return this;
    }

    public Builder operationTypes(final String value, final String... values) {
      return operationTypeOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder batchOperationTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        batchOperationTypeOperations = addValuesToList(batchOperationTypeOperations, operations);
      }
      return this;
    }

    public Builder batchOperationTypes(final String value, final String... values) {
      return batchOperationTypeOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder timestampOperations(final List<Operation<OffsetDateTime>> operations) {
      if (operations != null) {
        timestampOperations = addValuesToList(timestampOperations, operations);
      }
      return this;
    }

    public Builder timestamps(final OffsetDateTime value, final OffsetDateTime... values) {
      return timestampOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder actorTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        actorTypeOperations = addValuesToList(actorTypeOperations, operations);
      }
      return this;
    }

    public Builder actorTypes(final String value, final String... values) {
      return actorTypeOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder actorIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        actorIdOperations = addValuesToList(actorIdOperations, operations);
      }
      return this;
    }

    public Builder actorIds(final String value, final String... values) {
      return actorIdOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder agentElementIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        agentElementIdOperations = addValuesToList(agentElementIdOperations, operations);
      }
      return this;
    }

    public Builder agentElementIds(final String value, final String... values) {
      return agentElementIdOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      }
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder resultOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        resultOperations = addValuesToList(resultOperations, operations);
      }
      return this;
    }

    public Builder results(final String value, final String... values) {
      return resultOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder categoryOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        categoryOperations = addValuesToList(categoryOperations, operations);
      }
      return this;
    }

    public Builder categories(final String value, final String... values) {
      return categoryOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      }
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        processDefinitionKeyOperations =
            addValuesToList(processDefinitionKeyOperations, operations);
      }
      return this;
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      }
      return this;
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder userTaskKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        userTaskKeyOperations = addValuesToList(userTaskKeyOperations, operations);
      }
      return this;
    }

    public Builder userTaskKeys(final Long value, final Long... values) {
      return userTaskKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder decisionRequirementsIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        decisionRequirementsIdOperations =
            addValuesToList(decisionRequirementsIdOperations, operations);
      }
      return this;
    }

    public Builder decisionRequirementsIds(final String value, final String... values) {
      return decisionRequirementsIdOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder decisionRequirementsKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        decisionRequirementsKeyOperations =
            addValuesToList(decisionRequirementsKeyOperations, operations);
      }
      return this;
    }

    public Builder decisionRequirementsKeys(final Long value, final Long... values) {
      return decisionRequirementsKeyOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder decisionDefinitionIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        decisionDefinitionIdOperations =
            addValuesToList(decisionDefinitionIdOperations, operations);
      }
      return this;
    }

    public Builder decisionDefinitionIds(final String value, final String... values) {
      return decisionDefinitionIdOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder decisionDefinitionKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        decisionDefinitionKeyOperations =
            addValuesToList(decisionDefinitionKeyOperations, operations);
      }
      return this;
    }

    public Builder decisionDefinitionKeys(final Long value, final Long... values) {
      return decisionDefinitionKeyOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder decisionEvaluationKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        decisionEvaluationKeyOperations =
            addValuesToList(decisionEvaluationKeyOperations, operations);
      }
      return this;
    }

    public Builder decisionEvaluationKeys(final Long value, final Long... values) {
      return decisionEvaluationKeyOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder elementInstanceKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        elementInstanceKeyOperations = addValuesToList(elementInstanceKeyOperations, operations);
      }
      return this;
    }

    public Builder elementInstanceKeys(final Long value, final Long... values) {
      return elementInstanceKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder jobKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        jobKeyOperations = addValuesToList(jobKeyOperations, operations);
      }
      return this;
    }

    public Builder jobKeys(final Long value, final Long... values) {
      return jobKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder batchOperationKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        batchOperationKeyOperations = addValuesToList(batchOperationKeyOperations, operations);
      }
      return this;
    }

    public Builder batchOperationKeys(final Long value, final Long... values) {
      return batchOperationKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder deploymentKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        deploymentKeyOperations = addValuesToList(deploymentKeyOperations, operations);
      }
      return this;
    }

    public Builder deploymentKeys(final Long value, final Long... values) {
      return deploymentKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder formKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        formKeyOperations = addValuesToList(formKeyOperations, operations);
      }
      return this;
    }

    public Builder formKeys(final Long value, final Long... values) {
      return formKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder resourceKeyOperations(final List<Operation<Long>> operations) {
      if (operations != null) {
        resourceKeyOperations = addValuesToList(resourceKeyOperations, operations);
      }
      return this;
    }

    public Builder resourceKeys(final Long value, final Long... values) {
      return resourceKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder relatedEntityKeyOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        relatedEntityKeyOperations = addValuesToList(relatedEntityKeyOperations, operations);
      }
      return this;
    }

    public Builder relatedEntityKeys(final String value, final String... values) {
      return relatedEntityKeyOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder relatedEntityTypeOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        relatedEntityTypeOperations = addValuesToList(relatedEntityTypeOperations, operations);
      }
      return this;
    }

    public Builder relatedEntityTypes(final String value, final String... values) {
      return relatedEntityTypeOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    public Builder entityDescriptionOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        entityDescriptionOperations = addValuesToList(entityDescriptionOperations, operations);
      }
      return this;
    }

    public Builder entityDescriptions(final String value, final String... values) {
      return entityDescriptionOperations(List.of(FilterUtil.mapDefaultToOperation(value, values)));
    }

    @Override
    public AuditLogFilter build() {
      return new AuditLogFilter(
          Objects.requireNonNullElse(auditLogKeyOperations, List.of()),
          Objects.requireNonNullElse(entityKeyOperations, List.of()),
          Objects.requireNonNullElse(entityTypeOperations, List.of()),
          Objects.requireNonNullElse(operationTypeOperations, List.of()),
          Objects.requireNonNullElse(batchOperationTypeOperations, List.of()),
          Objects.requireNonNullElse(timestampOperations, List.of()),
          Objects.requireNonNullElse(actorTypeOperations, List.of()),
          Objects.requireNonNullElse(actorIdOperations, List.of()),
          Objects.requireNonNullElse(agentElementIdOperations, List.of()),
          Objects.requireNonNullElse(tenantIdOperations, List.of()),
          Objects.requireNonNullElse(resultOperations, List.of()),
          Objects.requireNonNullElse(categoryOperations, List.of()),
          Objects.requireNonNullElse(processInstanceKeyOperations, List.of()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, List.of()),
          Objects.requireNonNullElse(processDefinitionIdOperations, List.of()),
          Objects.requireNonNullElse(userTaskKeyOperations, List.of()),
          Objects.requireNonNullElse(decisionRequirementsIdOperations, List.of()),
          Objects.requireNonNullElse(decisionRequirementsKeyOperations, List.of()),
          Objects.requireNonNullElse(decisionDefinitionIdOperations, List.of()),
          Objects.requireNonNullElse(decisionDefinitionKeyOperations, List.of()),
          Objects.requireNonNullElse(decisionEvaluationKeyOperations, List.of()),
          Objects.requireNonNullElse(elementInstanceKeyOperations, List.of()),
          Objects.requireNonNullElse(jobKeyOperations, List.of()),
          Objects.requireNonNullElse(batchOperationKeyOperations, List.of()),
          Objects.requireNonNullElse(deploymentKeyOperations, List.of()),
          Objects.requireNonNullElse(formKeyOperations, List.of()),
          Objects.requireNonNullElse(resourceKeyOperations, List.of()),
          Objects.requireNonNullElse(relatedEntityKeyOperations, List.of()),
          Objects.requireNonNullElse(relatedEntityTypeOperations, List.of()),
          Objects.requireNonNullElse(entityDescriptionOperations, List.of()));
    }
  }
}
