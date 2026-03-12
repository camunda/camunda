/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobActivationRequestStrictContract(
    String type,
    @Nullable String worker,
    Long timeout,
    Integer maxJobsToActivate,
    @Nullable java.util.List<String> fetchVariable,
    @Nullable Long requestTimeout,
    @Nullable java.util.List<String> tenantIds,
    @Nullable io.camunda.gateway.protocol.model.TenantFilterEnum tenantFilter) {

  public GeneratedJobActivationRequestStrictContract {
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(timeout, "timeout is required and must not be null");
    Objects.requireNonNull(maxJobsToActivate, "maxJobsToActivate is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TypeStep, TimeoutStep, MaxJobsToActivateStep, OptionalStep {
    private String type;
    private ContractPolicy.FieldPolicy<String> typePolicy;
    private String worker;
    private Long timeout;
    private ContractPolicy.FieldPolicy<Long> timeoutPolicy;
    private Integer maxJobsToActivate;
    private ContractPolicy.FieldPolicy<Integer> maxJobsToActivatePolicy;
    private java.util.List<String> fetchVariable;
    private Long requestTimeout;
    private java.util.List<String> tenantIds;
    private io.camunda.gateway.protocol.model.TenantFilterEnum tenantFilter;

    private Builder() {}

    @Override
    public TimeoutStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = type;
      this.typePolicy = policy;
      return this;
    }

    @Override
    public MaxJobsToActivateStep timeout(
        final Long timeout, final ContractPolicy.FieldPolicy<Long> policy) {
      this.timeout = timeout;
      this.timeoutPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep maxJobsToActivate(
        final Integer maxJobsToActivate, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.maxJobsToActivate = maxJobsToActivate;
      this.maxJobsToActivatePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep worker(final String worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public OptionalStep worker(
        final String worker, final ContractPolicy.FieldPolicy<String> policy) {
      this.worker = policy.apply(worker, Fields.WORKER, null);
      return this;
    }

    @Override
    public OptionalStep fetchVariable(final java.util.List<String> fetchVariable) {
      this.fetchVariable = fetchVariable;
      return this;
    }

    @Override
    public OptionalStep fetchVariable(
        final java.util.List<String> fetchVariable,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.fetchVariable = policy.apply(fetchVariable, Fields.FETCH_VARIABLE, null);
      return this;
    }

    @Override
    public OptionalStep requestTimeout(final Long requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    @Override
    public OptionalStep requestTimeout(
        final Long requestTimeout, final ContractPolicy.FieldPolicy<Long> policy) {
      this.requestTimeout = policy.apply(requestTimeout, Fields.REQUEST_TIMEOUT, null);
      return this;
    }

    @Override
    public OptionalStep tenantIds(final java.util.List<String> tenantIds) {
      this.tenantIds = tenantIds;
      return this;
    }

    @Override
    public OptionalStep tenantIds(
        final java.util.List<String> tenantIds,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.tenantIds = policy.apply(tenantIds, Fields.TENANT_IDS, null);
      return this;
    }

    @Override
    public OptionalStep tenantFilter(
        final io.camunda.gateway.protocol.model.TenantFilterEnum tenantFilter) {
      this.tenantFilter = tenantFilter;
      return this;
    }

    @Override
    public OptionalStep tenantFilter(
        final io.camunda.gateway.protocol.model.TenantFilterEnum tenantFilter,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.TenantFilterEnum>
            policy) {
      this.tenantFilter = policy.apply(tenantFilter, Fields.TENANT_FILTER, null);
      return this;
    }

    @Override
    public GeneratedJobActivationRequestStrictContract build() {
      return new GeneratedJobActivationRequestStrictContract(
          applyRequiredPolicy(this.type, this.typePolicy, Fields.TYPE),
          this.worker,
          applyRequiredPolicy(this.timeout, this.timeoutPolicy, Fields.TIMEOUT),
          applyRequiredPolicy(
              this.maxJobsToActivate, this.maxJobsToActivatePolicy, Fields.MAX_JOBS_TO_ACTIVATE),
          this.fetchVariable,
          this.requestTimeout,
          this.tenantIds,
          this.tenantFilter);
    }
  }

  public interface TypeStep {
    TimeoutStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TimeoutStep {
    MaxJobsToActivateStep timeout(
        final Long timeout, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface MaxJobsToActivateStep {
    OptionalStep maxJobsToActivate(
        final Integer maxJobsToActivate, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    OptionalStep worker(final String worker);

    OptionalStep worker(final String worker, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep fetchVariable(final java.util.List<String> fetchVariable);

    OptionalStep fetchVariable(
        final java.util.List<String> fetchVariable,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep requestTimeout(final Long requestTimeout);

    OptionalStep requestTimeout(
        final Long requestTimeout, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep tenantIds(final java.util.List<String> tenantIds);

    OptionalStep tenantIds(
        final java.util.List<String> tenantIds,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep tenantFilter(
        final io.camunda.gateway.protocol.model.TenantFilterEnum tenantFilter);

    OptionalStep tenantFilter(
        final io.camunda.gateway.protocol.model.TenantFilterEnum tenantFilter,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.TenantFilterEnum>
            policy);

    GeneratedJobActivationRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("JobActivationRequest", "type");
    public static final ContractPolicy.FieldRef WORKER =
        ContractPolicy.field("JobActivationRequest", "worker");
    public static final ContractPolicy.FieldRef TIMEOUT =
        ContractPolicy.field("JobActivationRequest", "timeout");
    public static final ContractPolicy.FieldRef MAX_JOBS_TO_ACTIVATE =
        ContractPolicy.field("JobActivationRequest", "maxJobsToActivate");
    public static final ContractPolicy.FieldRef FETCH_VARIABLE =
        ContractPolicy.field("JobActivationRequest", "fetchVariable");
    public static final ContractPolicy.FieldRef REQUEST_TIMEOUT =
        ContractPolicy.field("JobActivationRequest", "requestTimeout");
    public static final ContractPolicy.FieldRef TENANT_IDS =
        ContractPolicy.field("JobActivationRequest", "tenantIds");
    public static final ContractPolicy.FieldRef TENANT_FILTER =
        ContractPolicy.field("JobActivationRequest", "tenantFilter");

    private Fields() {}
  }
}
