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
import org.jspecify.annotations.NullMarked;

@NullMarked
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

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TenantIdStep, FormIdStep, SchemaStep, VersionStep, FormKeyStep, OptionalStep {
    private String tenantId;
    private String formId;
    private String schema;
    private Long version;
    private Object formKey;

    private Builder() {}

    @Override
    public FormIdStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public SchemaStep formId(final String formId) {
      this.formId = formId;
      return this;
    }

    @Override
    public VersionStep schema(final String schema) {
      this.schema = schema;
      return this;
    }

    @Override
    public FormKeyStep version(final Long version) {
      this.version = version;
      return this;
    }

    @Override
    public OptionalStep formKey(final Object formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public GeneratedFormStrictContract build() {
      return new GeneratedFormStrictContract(
          this.tenantId, this.formId, this.schema, this.version, coerceFormKey(this.formKey));
    }
  }

  public interface TenantIdStep {
    FormIdStep tenantId(final String tenantId);
  }

  public interface FormIdStep {
    SchemaStep formId(final String formId);
  }

  public interface SchemaStep {
    VersionStep schema(final String schema);
  }

  public interface VersionStep {
    FormKeyStep version(final Long version);
  }

  public interface FormKeyStep {
    OptionalStep formKey(final Object formKey);
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
