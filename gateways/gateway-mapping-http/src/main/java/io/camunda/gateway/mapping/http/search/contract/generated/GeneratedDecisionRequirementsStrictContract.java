/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-requirements.yaml#/components/schemas/DecisionRequirementsResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionRequirementsStrictContract(
    String decisionRequirementsId,
    String decisionRequirementsKey,
    String decisionRequirementsName,
    String resourceName,
    String tenantId,
    Integer version
) {

  public GeneratedDecisionRequirementsStrictContract {
    Objects.requireNonNull(decisionRequirementsId, "decisionRequirementsId is required and must not be null");
    Objects.requireNonNull(decisionRequirementsKey, "decisionRequirementsKey is required and must not be null");
    Objects.requireNonNull(decisionRequirementsName, "decisionRequirementsName is required and must not be null");
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
  }

  public static String coerceDecisionRequirementsKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "decisionRequirementsKey must be a String or Number, but was " + value.getClass().getName());
  }



  public static DecisionRequirementsIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements DecisionRequirementsIdStep, DecisionRequirementsKeyStep, DecisionRequirementsNameStep, ResourceNameStep, TenantIdStep, VersionStep, OptionalStep {
    private String decisionRequirementsId;
    private Object decisionRequirementsKey;
    private String decisionRequirementsName;
    private String resourceName;
    private String tenantId;
    private Integer version;

    private Builder() {}

    @Override
    public DecisionRequirementsKeyStep decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public DecisionRequirementsNameStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public ResourceNameStep decisionRequirementsName(final String decisionRequirementsName) {
      this.decisionRequirementsName = decisionRequirementsName;
      return this;
    }

    @Override
    public TenantIdStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public VersionStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep version(final Integer version) {
      this.version = version;
      return this;
    }
    @Override
    public GeneratedDecisionRequirementsStrictContract build() {
      return new GeneratedDecisionRequirementsStrictContract(
          this.decisionRequirementsId,
          coerceDecisionRequirementsKey(this.decisionRequirementsKey),
          this.decisionRequirementsName,
          this.resourceName,
          this.tenantId,
          this.version);
    }
  }

  public interface DecisionRequirementsIdStep {
    DecisionRequirementsKeyStep decisionRequirementsId(final String decisionRequirementsId);
  }

  public interface DecisionRequirementsKeyStep {
    DecisionRequirementsNameStep decisionRequirementsKey(final Object decisionRequirementsKey);
  }

  public interface DecisionRequirementsNameStep {
    ResourceNameStep decisionRequirementsName(final String decisionRequirementsName);
  }

  public interface ResourceNameStep {
    TenantIdStep resourceName(final String resourceName);
  }

  public interface TenantIdStep {
    VersionStep tenantId(final String tenantId);
  }

  public interface VersionStep {
    OptionalStep version(final Integer version);
  }

  public interface OptionalStep {
    GeneratedDecisionRequirementsStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID = ContractPolicy.field("DecisionRequirementsResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY = ContractPolicy.field("DecisionRequirementsResult", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_NAME = ContractPolicy.field("DecisionRequirementsResult", "decisionRequirementsName");
    public static final ContractPolicy.FieldRef RESOURCE_NAME = ContractPolicy.field("DecisionRequirementsResult", "resourceName");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("DecisionRequirementsResult", "tenantId");
    public static final ContractPolicy.FieldRef VERSION = ContractPolicy.field("DecisionRequirementsResult", "version");

    private Fields() {}
  }


}
