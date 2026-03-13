/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedLicenseResponseStrictContract(
    Boolean validLicense, String licenseType, Boolean isCommercial, @Nullable String expiresAt) {

  public GeneratedLicenseResponseStrictContract {
    Objects.requireNonNull(validLicense, "validLicense is required and must not be null");
    Objects.requireNonNull(licenseType, "licenseType is required and must not be null");
    Objects.requireNonNull(isCommercial, "isCommercial is required and must not be null");
  }

  public static ValidLicenseStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ValidLicenseStep, LicenseTypeStep, IsCommercialStep, OptionalStep {
    private Boolean validLicense;
    private String licenseType;
    private Boolean isCommercial;
    private String expiresAt;

    private Builder() {}

    @Override
    public LicenseTypeStep validLicense(final Boolean validLicense) {
      this.validLicense = validLicense;
      return this;
    }

    @Override
    public IsCommercialStep licenseType(final String licenseType) {
      this.licenseType = licenseType;
      return this;
    }

    @Override
    public OptionalStep isCommercial(final Boolean isCommercial) {
      this.isCommercial = isCommercial;
      return this;
    }

    @Override
    public OptionalStep expiresAt(final @Nullable String expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    @Override
    public OptionalStep expiresAt(
        final @Nullable String expiresAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.expiresAt = policy.apply(expiresAt, Fields.EXPIRES_AT, null);
      return this;
    }

    @Override
    public GeneratedLicenseResponseStrictContract build() {
      return new GeneratedLicenseResponseStrictContract(
          this.validLicense, this.licenseType, this.isCommercial, this.expiresAt);
    }
  }

  public interface ValidLicenseStep {
    LicenseTypeStep validLicense(final Boolean validLicense);
  }

  public interface LicenseTypeStep {
    IsCommercialStep licenseType(final String licenseType);
  }

  public interface IsCommercialStep {
    OptionalStep isCommercial(final Boolean isCommercial);
  }

  public interface OptionalStep {
    OptionalStep expiresAt(final @Nullable String expiresAt);

    OptionalStep expiresAt(
        final @Nullable String expiresAt, final ContractPolicy.FieldPolicy<String> policy);

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
