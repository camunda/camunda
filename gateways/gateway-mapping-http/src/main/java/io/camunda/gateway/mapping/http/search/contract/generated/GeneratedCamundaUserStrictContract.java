/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCamundaUserStrictContract(
    @JsonProperty("username") @Nullable String username,
    @JsonProperty("displayName") @Nullable String displayName,
    @JsonProperty("email") @Nullable String email,
    @JsonProperty("authorizedComponents") java.util.List<String> authorizedComponents,
    @JsonProperty("tenants") java.util.List<GeneratedTenantStrictContract> tenants,
    @JsonProperty("groups") java.util.List<String> groups,
    @JsonProperty("roles") java.util.List<String> roles,
    @JsonProperty("salesPlanType") @Nullable String salesPlanType,
    @JsonProperty("c8Links") java.util.Map<String, String> c8Links,
    @JsonProperty("canLogout") Boolean canLogout) {

  public GeneratedCamundaUserStrictContract {
    Objects.requireNonNull(authorizedComponents, "No authorizedComponents provided.");
    Objects.requireNonNull(tenants, "No tenants provided.");
    Objects.requireNonNull(groups, "No groups provided.");
    Objects.requireNonNull(roles, "No roles provided.");
    Objects.requireNonNull(c8Links, "No c8Links provided.");
    Objects.requireNonNull(canLogout, "No canLogout provided.");
  }

  public static java.util.List<GeneratedTenantStrictContract> coerceTenants(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "tenants must be a List of GeneratedTenantStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedTenantStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedTenantStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "tenants must contain only GeneratedTenantStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static AuthorizedComponentsStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements AuthorizedComponentsStep,
          TenantsStep,
          GroupsStep,
          RolesStep,
          C8LinksStep,
          CanLogoutStep,
          OptionalStep {
    private String username;
    private String displayName;
    private String email;
    private java.util.List<String> authorizedComponents;
    private Object tenants;
    private java.util.List<String> groups;
    private java.util.List<String> roles;
    private String salesPlanType;
    private java.util.Map<String, String> c8Links;
    private Boolean canLogout;

    private Builder() {}

    @Override
    public TenantsStep authorizedComponents(final java.util.List<String> authorizedComponents) {
      this.authorizedComponents = authorizedComponents;
      return this;
    }

    @Override
    public GroupsStep tenants(final Object tenants) {
      this.tenants = tenants;
      return this;
    }

    @Override
    public RolesStep groups(final java.util.List<String> groups) {
      this.groups = groups;
      return this;
    }

    @Override
    public C8LinksStep roles(final java.util.List<String> roles) {
      this.roles = roles;
      return this;
    }

    @Override
    public CanLogoutStep c8Links(final java.util.Map<String, String> c8Links) {
      this.c8Links = c8Links;
      return this;
    }

    @Override
    public OptionalStep canLogout(final Boolean canLogout) {
      this.canLogout = canLogout;
      return this;
    }

    @Override
    public OptionalStep username(final @Nullable String username) {
      this.username = username;
      return this;
    }

    @Override
    public OptionalStep username(
        final @Nullable String username, final ContractPolicy.FieldPolicy<String> policy) {
      this.username = policy.apply(username, Fields.USERNAME, null);
      return this;
    }

    @Override
    public OptionalStep displayName(final @Nullable String displayName) {
      this.displayName = displayName;
      return this;
    }

    @Override
    public OptionalStep displayName(
        final @Nullable String displayName, final ContractPolicy.FieldPolicy<String> policy) {
      this.displayName = policy.apply(displayName, Fields.DISPLAY_NAME, null);
      return this;
    }

    @Override
    public OptionalStep email(final @Nullable String email) {
      this.email = email;
      return this;
    }

    @Override
    public OptionalStep email(
        final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy) {
      this.email = policy.apply(email, Fields.EMAIL, null);
      return this;
    }

    @Override
    public OptionalStep salesPlanType(final @Nullable String salesPlanType) {
      this.salesPlanType = salesPlanType;
      return this;
    }

    @Override
    public OptionalStep salesPlanType(
        final @Nullable String salesPlanType, final ContractPolicy.FieldPolicy<String> policy) {
      this.salesPlanType = policy.apply(salesPlanType, Fields.SALES_PLAN_TYPE, null);
      return this;
    }

    @Override
    public GeneratedCamundaUserStrictContract build() {
      return new GeneratedCamundaUserStrictContract(
          this.username,
          this.displayName,
          this.email,
          this.authorizedComponents,
          coerceTenants(this.tenants),
          this.groups,
          this.roles,
          this.salesPlanType,
          this.c8Links,
          this.canLogout);
    }
  }

  public interface AuthorizedComponentsStep {
    TenantsStep authorizedComponents(final java.util.List<String> authorizedComponents);
  }

  public interface TenantsStep {
    GroupsStep tenants(final Object tenants);
  }

  public interface GroupsStep {
    RolesStep groups(final java.util.List<String> groups);
  }

  public interface RolesStep {
    C8LinksStep roles(final java.util.List<String> roles);
  }

  public interface C8LinksStep {
    CanLogoutStep c8Links(final java.util.Map<String, String> c8Links);
  }

  public interface CanLogoutStep {
    OptionalStep canLogout(final Boolean canLogout);
  }

  public interface OptionalStep {
    OptionalStep username(final @Nullable String username);

    OptionalStep username(
        final @Nullable String username, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep displayName(final @Nullable String displayName);

    OptionalStep displayName(
        final @Nullable String displayName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final @Nullable String email);

    OptionalStep email(
        final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep salesPlanType(final @Nullable String salesPlanType);

    OptionalStep salesPlanType(
        final @Nullable String salesPlanType, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedCamundaUserStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("CamundaUserResult", "username");
    public static final ContractPolicy.FieldRef DISPLAY_NAME =
        ContractPolicy.field("CamundaUserResult", "displayName");
    public static final ContractPolicy.FieldRef EMAIL =
        ContractPolicy.field("CamundaUserResult", "email");
    public static final ContractPolicy.FieldRef AUTHORIZED_COMPONENTS =
        ContractPolicy.field("CamundaUserResult", "authorizedComponents");
    public static final ContractPolicy.FieldRef TENANTS =
        ContractPolicy.field("CamundaUserResult", "tenants");
    public static final ContractPolicy.FieldRef GROUPS =
        ContractPolicy.field("CamundaUserResult", "groups");
    public static final ContractPolicy.FieldRef ROLES =
        ContractPolicy.field("CamundaUserResult", "roles");
    public static final ContractPolicy.FieldRef SALES_PLAN_TYPE =
        ContractPolicy.field("CamundaUserResult", "salesPlanType");
    public static final ContractPolicy.FieldRef C8_LINKS =
        ContractPolicy.field("CamundaUserResult", "c8Links");
    public static final ContractPolicy.FieldRef CAN_LOGOUT =
        ContractPolicy.field("CamundaUserResult", "canLogout");

    private Fields() {}
  }
}
