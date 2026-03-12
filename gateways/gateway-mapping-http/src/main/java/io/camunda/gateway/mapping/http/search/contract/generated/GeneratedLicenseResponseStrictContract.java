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
public record GeneratedLicenseResponseStrictContract(
    Boolean validLicense, String licenseType, Boolean isCommercial, @Nullable String expiresAt) {

  public GeneratedLicenseResponseStrictContract {
    Objects.requireNonNull(validLicense, "validLicense is required and must not be null");
    Objects.requireNonNull(licenseType, "licenseType is required and must not be null");
    Objects.requireNonNull(isCommercial, "isCommercial is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ValidLicenseStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ValidLicenseStep, LicenseTypeStep, IsCommercialStep, OptionalStep {
    private Boolean validLicense;
    private ContractPolicy.FieldPolicy<Boolean> validLicensePolicy;
    private String licenseType;
    private ContractPolicy.FieldPolicy<String> licenseTypePolicy;
    private Boolean isCommercial;
    private ContractPolicy.FieldPolicy<Boolean> isCommercialPolicy;
    private String expiresAt;

    private Builder() {}

    @Override
    public LicenseTypeStep validLicense(
        final Boolean validLicense, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.validLicense = validLicense;
      this.validLicensePolicy = policy;
      return this;
    }

    @Override
    public IsCommercialStep licenseType(
        final String licenseType, final ContractPolicy.FieldPolicy<String> policy) {
      this.licenseType = licenseType;
      this.licenseTypePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep isCommercial(
        final Boolean isCommercial, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isCommercial = isCommercial;
      this.isCommercialPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep expiresAt(final String expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    @Override
    public OptionalStep expiresAt(
        final String expiresAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.expiresAt = policy.apply(expiresAt, Fields.EXPIRES_AT, null);
      return this;
    }

    @Override
    public GeneratedLicenseResponseStrictContract build() {
      return new GeneratedLicenseResponseStrictContract(
          applyRequiredPolicy(this.validLicense, this.validLicensePolicy, Fields.VALID_LICENSE),
          applyRequiredPolicy(this.licenseType, this.licenseTypePolicy, Fields.LICENSE_TYPE),
          applyRequiredPolicy(this.isCommercial, this.isCommercialPolicy, Fields.IS_COMMERCIAL),
          this.expiresAt);
    }
  }

  public interface ValidLicenseStep {
    LicenseTypeStep validLicense(
        final Boolean validLicense, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface LicenseTypeStep {
    IsCommercialStep licenseType(
        final String licenseType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface IsCommercialStep {
    OptionalStep isCommercial(
        final Boolean isCommercial, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface OptionalStep {
    OptionalStep expiresAt(final String expiresAt);

    OptionalStep expiresAt(final String expiresAt, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedLicenseResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALID_LICENSE =
        ContractPolicy.field("LicenseResponse", "validLicense");
    public static final ContractPolicy.FieldRef LICENSE_TYPE =
        ContractPolicy.field("LicenseResponse", "licenseType");
    public static final ContractPolicy.FieldRef IS_COMMERCIAL =
        ContractPolicy.field("LicenseResponse", "isCommercial");
    public static final ContractPolicy.FieldRef EXPIRES_AT =
        ContractPolicy.field("LicenseResponse", "expiresAt");

    private Fields() {}
  }
}
