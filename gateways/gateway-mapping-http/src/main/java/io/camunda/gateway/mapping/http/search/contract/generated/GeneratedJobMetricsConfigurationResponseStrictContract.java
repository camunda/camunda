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
import org.jspecify.annotations.NullMarked;

@NullMarked
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
    private String exportInterval;
    private Integer maxWorkerNameLength;
    private Integer maxJobTypeLength;
    private Integer maxTenantIdLength;
    private Integer maxUniqueKeys;

    private Builder() {}

    @Override
    public ExportIntervalStep enabled(final Boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    @Override
    public MaxWorkerNameLengthStep exportInterval(final String exportInterval) {
      this.exportInterval = exportInterval;
      return this;
    }

    @Override
    public MaxJobTypeLengthStep maxWorkerNameLength(final Integer maxWorkerNameLength) {
      this.maxWorkerNameLength = maxWorkerNameLength;
      return this;
    }

    @Override
    public MaxTenantIdLengthStep maxJobTypeLength(final Integer maxJobTypeLength) {
      this.maxJobTypeLength = maxJobTypeLength;
      return this;
    }

    @Override
    public MaxUniqueKeysStep maxTenantIdLength(final Integer maxTenantIdLength) {
      this.maxTenantIdLength = maxTenantIdLength;
      return this;
    }

    @Override
    public OptionalStep maxUniqueKeys(final Integer maxUniqueKeys) {
      this.maxUniqueKeys = maxUniqueKeys;
      return this;
    }

    @Override
    public GeneratedJobMetricsConfigurationResponseStrictContract build() {
      return new GeneratedJobMetricsConfigurationResponseStrictContract(
          this.enabled,
          this.exportInterval,
          this.maxWorkerNameLength,
          this.maxJobTypeLength,
          this.maxTenantIdLength,
          this.maxUniqueKeys);
    }
  }

  public interface EnabledStep {
    ExportIntervalStep enabled(final Boolean enabled);
  }

  public interface ExportIntervalStep {
    MaxWorkerNameLengthStep exportInterval(final String exportInterval);
  }

  public interface MaxWorkerNameLengthStep {
    MaxJobTypeLengthStep maxWorkerNameLength(final Integer maxWorkerNameLength);
  }

  public interface MaxJobTypeLengthStep {
    MaxTenantIdLengthStep maxJobTypeLength(final Integer maxJobTypeLength);
  }

  public interface MaxTenantIdLengthStep {
    MaxUniqueKeysStep maxTenantIdLength(final Integer maxTenantIdLength);
  }

  public interface MaxUniqueKeysStep {
    OptionalStep maxUniqueKeys(final Integer maxUniqueKeys);
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
