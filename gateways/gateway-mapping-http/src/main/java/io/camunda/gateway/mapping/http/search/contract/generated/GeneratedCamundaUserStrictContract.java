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
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCamundaUserStrictContract(
    @Nullable String username,
    @Nullable String displayName,
    @Nullable String email,
    java.util.List<String> authorizedComponents,
    java.util.List<GeneratedTenantStrictContract> tenants,
    java.util.List<String> groups,
    java.util.List<String> roles,
    @Nullable String salesPlanType,
    java.util.Map<String, String> c8Links,
    Boolean canLogout) {

  public GeneratedCamundaUserStrictContract {
    Objects.requireNonNull(
        authorizedComponents, "authorizedComponents is required and must not be null");
    Objects.requireNonNull(tenants, "tenants is required and must not be null");
    Objects.requireNonNull(groups, "groups is required and must not be null");
    Objects.requireNonNull(roles, "roles is required and must not be null");
    Objects.requireNonNull(c8Links, "c8Links is required and must not be null");
    Objects.requireNonNull(canLogout, "canLogout is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<java.util.List<String>> authorizedComponentsPolicy;
    private Object tenants;
    private ContractPolicy.FieldPolicy<Object> tenantsPolicy;
    private java.util.List<String> groups;
    private ContractPolicy.FieldPolicy<java.util.List<String>> groupsPolicy;
    private java.util.List<String> roles;
    private ContractPolicy.FieldPolicy<java.util.List<String>> rolesPolicy;
    private String salesPlanType;
    private java.util.Map<String, String> c8Links;
    private ContractPolicy.FieldPolicy<java.util.Map<String, String>> c8LinksPolicy;
    private Boolean canLogout;
    private ContractPolicy.FieldPolicy<Boolean> canLogoutPolicy;

    private Builder() {}

    @Override
    public TenantsStep authorizedComponents(
        final java.util.List<String> authorizedComponents,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.authorizedComponents = authorizedComponents;
      this.authorizedComponentsPolicy = policy;
      return this;
    }

    @Override
    public GroupsStep tenants(
        final Object tenants, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenants = tenants;
      this.tenantsPolicy = policy;
      return this;
    }

    @Override
    public RolesStep groups(
        final java.util.List<String> groups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.groups = groups;
      this.groupsPolicy = policy;
      return this;
    }

    @Override
    public C8LinksStep roles(
        final java.util.List<String> roles,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.roles = roles;
      this.rolesPolicy = policy;
      return this;
    }

    @Override
    public CanLogoutStep c8Links(
        final java.util.Map<String, String> c8Links,
        final ContractPolicy.FieldPolicy<java.util.Map<String, String>> policy) {
      this.c8Links = c8Links;
      this.c8LinksPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep canLogout(
        final Boolean canLogout, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.canLogout = canLogout;
      this.canLogoutPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep username(final String username) {
      this.username = username;
      return this;
    }

    @Override
    public OptionalStep username(
        final String username, final ContractPolicy.FieldPolicy<String> policy) {
      this.username = policy.apply(username, Fields.USERNAME, null);
      return this;
    }

    @Override
    public OptionalStep displayName(final String displayName) {
      this.displayName = displayName;
      return this;
    }

    @Override
    public OptionalStep displayName(
        final String displayName, final ContractPolicy.FieldPolicy<String> policy) {
      this.displayName = policy.apply(displayName, Fields.DISPLAY_NAME, null);
      return this;
    }

    @Override
    public OptionalStep email(final String email) {
      this.email = email;
      return this;
    }

    @Override
    public OptionalStep email(final String email, final ContractPolicy.FieldPolicy<String> policy) {
      this.email = policy.apply(email, Fields.EMAIL, null);
      return this;
    }

    @Override
    public OptionalStep salesPlanType(final String salesPlanType) {
      this.salesPlanType = salesPlanType;
      return this;
    }

    @Override
    public OptionalStep salesPlanType(
        final String salesPlanType, final ContractPolicy.FieldPolicy<String> policy) {
      this.salesPlanType = policy.apply(salesPlanType, Fields.SALES_PLAN_TYPE, null);
      return this;
    }

    @Override
    public GeneratedCamundaUserStrictContract build() {
      return new GeneratedCamundaUserStrictContract(
          this.username,
          this.displayName,
          this.email,
          applyRequiredPolicy(
              this.authorizedComponents,
              this.authorizedComponentsPolicy,
              Fields.AUTHORIZED_COMPONENTS),
          coerceTenants(applyRequiredPolicy(this.tenants, this.tenantsPolicy, Fields.TENANTS)),
          applyRequiredPolicy(this.groups, this.groupsPolicy, Fields.GROUPS),
          applyRequiredPolicy(this.roles, this.rolesPolicy, Fields.ROLES),
          this.salesPlanType,
          applyRequiredPolicy(this.c8Links, this.c8LinksPolicy, Fields.C8_LINKS),
          applyRequiredPolicy(this.canLogout, this.canLogoutPolicy, Fields.CAN_LOGOUT));
    }
  }

  public interface AuthorizedComponentsStep {
    TenantsStep authorizedComponents(
        final java.util.List<String> authorizedComponents,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface TenantsStep {
    GroupsStep tenants(final Object tenants, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface GroupsStep {
    RolesStep groups(
        final java.util.List<String> groups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface RolesStep {
    C8LinksStep roles(
        final java.util.List<String> roles,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface C8LinksStep {
    CanLogoutStep c8Links(
        final java.util.Map<String, String> c8Links,
        final ContractPolicy.FieldPolicy<java.util.Map<String, String>> policy);
  }

  public interface CanLogoutStep {
    OptionalStep canLogout(
        final Boolean canLogout, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface OptionalStep {
    OptionalStep username(final String username);

    OptionalStep username(final String username, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep displayName(final String displayName);

    OptionalStep displayName(
        final String displayName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final String email);

    OptionalStep email(final String email, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep salesPlanType(final String salesPlanType);

    OptionalStep salesPlanType(
        final String salesPlanType, final ContractPolicy.FieldPolicy<String> policy);

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
