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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobMetricsConfigurationResponseStrictContract(
    Boolean enabled,
    String exportInterval,
    Integer maxWorkerNameLength,
    Integer maxJobTypeLength,
    Integer maxTenantIdLength,
    Integer maxUniqueKeys) {

  public GeneratedJobMetricsConfigurationResponseStrictContract {
    Objects.requireNonNull(enabled, "enabled is required and must not be null");
    Objects.requireNonNull(exportInterval, "exportInterval is required and must not be null");
    Objects.requireNonNull(
        maxWorkerNameLength, "maxWorkerNameLength is required and must not be null");
    Objects.requireNonNull(maxJobTypeLength, "maxJobTypeLength is required and must not be null");
    Objects.requireNonNull(maxTenantIdLength, "maxTenantIdLength is required and must not be null");
    Objects.requireNonNull(maxUniqueKeys, "maxUniqueKeys is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static EnabledStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements EnabledStep,
          ExportIntervalStep,
          MaxWorkerNameLengthStep,
          MaxJobTypeLengthStep,
          MaxTenantIdLengthStep,
          MaxUniqueKeysStep,
          OptionalStep {
    private Boolean enabled;
    private ContractPolicy.FieldPolicy<Boolean> enabledPolicy;
    private String exportInterval;
    private ContractPolicy.FieldPolicy<String> exportIntervalPolicy;
    private Integer maxWorkerNameLength;
    private ContractPolicy.FieldPolicy<Integer> maxWorkerNameLengthPolicy;
    private Integer maxJobTypeLength;
    private ContractPolicy.FieldPolicy<Integer> maxJobTypeLengthPolicy;
    private Integer maxTenantIdLength;
    private ContractPolicy.FieldPolicy<Integer> maxTenantIdLengthPolicy;
    private Integer maxUniqueKeys;
    private ContractPolicy.FieldPolicy<Integer> maxUniqueKeysPolicy;

    private Builder() {}

    @Override
    public ExportIntervalStep enabled(
        final Boolean enabled, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.enabled = enabled;
      this.enabledPolicy = policy;
      return this;
    }

    @Override
    public MaxWorkerNameLengthStep exportInterval(
        final String exportInterval, final ContractPolicy.FieldPolicy<String> policy) {
      this.exportInterval = exportInterval;
      this.exportIntervalPolicy = policy;
      return this;
    }

    @Override
    public MaxJobTypeLengthStep maxWorkerNameLength(
        final Integer maxWorkerNameLength, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.maxWorkerNameLength = maxWorkerNameLength;
      this.maxWorkerNameLengthPolicy = policy;
      return this;
    }

    @Override
    public MaxTenantIdLengthStep maxJobTypeLength(
        final Integer maxJobTypeLength, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.maxJobTypeLength = maxJobTypeLength;
      this.maxJobTypeLengthPolicy = policy;
      return this;
    }

    @Override
    public MaxUniqueKeysStep maxTenantIdLength(
        final Integer maxTenantIdLength, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.maxTenantIdLength = maxTenantIdLength;
      this.maxTenantIdLengthPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep maxUniqueKeys(
        final Integer maxUniqueKeys, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.maxUniqueKeys = maxUniqueKeys;
      this.maxUniqueKeysPolicy = policy;
      return this;
    }

    @Override
    public GeneratedJobMetricsConfigurationResponseStrictContract build() {
      return new GeneratedJobMetricsConfigurationResponseStrictContract(
          applyRequiredPolicy(this.enabled, this.enabledPolicy, Fields.ENABLED),
          applyRequiredPolicy(
              this.exportInterval, this.exportIntervalPolicy, Fields.EXPORT_INTERVAL),
          applyRequiredPolicy(
              this.maxWorkerNameLength,
              this.maxWorkerNameLengthPolicy,
              Fields.MAX_WORKER_NAME_LENGTH),
          applyRequiredPolicy(
              this.maxJobTypeLength, this.maxJobTypeLengthPolicy, Fields.MAX_JOB_TYPE_LENGTH),
          applyRequiredPolicy(
              this.maxTenantIdLength, this.maxTenantIdLengthPolicy, Fields.MAX_TENANT_ID_LENGTH),
          applyRequiredPolicy(
              this.maxUniqueKeys, this.maxUniqueKeysPolicy, Fields.MAX_UNIQUE_KEYS));
    }
  }

  public interface EnabledStep {
    ExportIntervalStep enabled(
        final Boolean enabled, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface ExportIntervalStep {
    MaxWorkerNameLengthStep exportInterval(
        final String exportInterval, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MaxWorkerNameLengthStep {
    MaxJobTypeLengthStep maxWorkerNameLength(
        final Integer maxWorkerNameLength, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface MaxJobTypeLengthStep {
    MaxTenantIdLengthStep maxJobTypeLength(
        final Integer maxJobTypeLength, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface MaxTenantIdLengthStep {
    MaxUniqueKeysStep maxTenantIdLength(
        final Integer maxTenantIdLength, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface MaxUniqueKeysStep {
    OptionalStep maxUniqueKeys(
        final Integer maxUniqueKeys, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    GeneratedJobMetricsConfigurationResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ENABLED =
        ContractPolicy.field("JobMetricsConfigurationResponse", "enabled");
    public static final ContractPolicy.FieldRef EXPORT_INTERVAL =
        ContractPolicy.field("JobMetricsConfigurationResponse", "exportInterval");
    public static final ContractPolicy.FieldRef MAX_WORKER_NAME_LENGTH =
        ContractPolicy.field("JobMetricsConfigurationResponse", "maxWorkerNameLength");
    public static final ContractPolicy.FieldRef MAX_JOB_TYPE_LENGTH =
        ContractPolicy.field("JobMetricsConfigurationResponse", "maxJobTypeLength");
    public static final ContractPolicy.FieldRef MAX_TENANT_ID_LENGTH =
        ContractPolicy.field("JobMetricsConfigurationResponse", "maxTenantIdLength");
    public static final ContractPolicy.FieldRef MAX_UNIQUE_KEYS =
        ContractPolicy.field("JobMetricsConfigurationResponse", "maxUniqueKeys");

    private Fields() {}
  }
}
