/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedFormStrictContract(
    String tenantId, String formId, String schema, Long version, String formKey) {

  public GeneratedFormStrictContract {
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(formId, "formId is required and must not be null");
    Objects.requireNonNull(schema, "schema is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
    Objects.requireNonNull(formKey, "formKey is required and must not be null");
  }

  public static String coerceFormKey(final Object value) {
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
        "formKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TenantIdStep, FormIdStep, SchemaStep, VersionStep, FormKeyStep, OptionalStep {
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String formId;
    private ContractPolicy.FieldPolicy<String> formIdPolicy;
    private String schema;
    private ContractPolicy.FieldPolicy<String> schemaPolicy;
    private Long version;
    private ContractPolicy.FieldPolicy<Long> versionPolicy;
    private Object formKey;
    private ContractPolicy.FieldPolicy<Object> formKeyPolicy;

    private Builder() {}

    @Override
    public FormIdStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public SchemaStep formId(final String formId, final ContractPolicy.FieldPolicy<String> policy) {
      this.formId = formId;
      this.formIdPolicy = policy;
      return this;
    }

    @Override
    public VersionStep schema(
        final String schema, final ContractPolicy.FieldPolicy<String> policy) {
      this.schema = schema;
      this.schemaPolicy = policy;
      return this;
    }

    @Override
    public FormKeyStep version(final Long version, final ContractPolicy.FieldPolicy<Long> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep formKey(
        final Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = formKey;
      this.formKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedFormStrictContract build() {
      return new GeneratedFormStrictContract(
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.formId, this.formIdPolicy, Fields.FORM_ID),
          applyRequiredPolicy(this.schema, this.schemaPolicy, Fields.SCHEMA),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION),
          coerceFormKey(applyRequiredPolicy(this.formKey, this.formKeyPolicy, Fields.FORM_KEY)));
    }
  }

  public interface TenantIdStep {
    FormIdStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface FormIdStep {
    SchemaStep formId(final String formId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface SchemaStep {
    VersionStep schema(final String schema, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    FormKeyStep version(final Long version, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface FormKeyStep {
    OptionalStep formKey(final Object formKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedFormStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("FormResult", "tenantId");
    public static final ContractPolicy.FieldRef FORM_ID =
        ContractPolicy.field("FormResult", "formId");
    public static final ContractPolicy.FieldRef SCHEMA =
        ContractPolicy.field("FormResult", "schema");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("FormResult", "version");
    public static final ContractPolicy.FieldRef FORM_KEY =
        ContractPolicy.field("FormResult", "formKey");

    private Fields() {}
  }
}
