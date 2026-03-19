/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/form-models.yaml#/components/schemas/FormResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedFormStrictContract(
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("formId") String formId,
    @JsonProperty("schema") String schema,
    @JsonProperty("version") Long version,
    @JsonProperty("formKey") String formKey) {

  public GeneratedFormStrictContract {
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(formId, "No formId provided.");
    Objects.requireNonNull(schema, "No schema provided.");
    Objects.requireNonNull(version, "No version provided.");
    Objects.requireNonNull(formKey, "No formKey provided.");
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
