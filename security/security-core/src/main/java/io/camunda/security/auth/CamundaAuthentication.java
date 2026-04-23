/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents the authentication context for a user or client in Camunda, including (where
 * appropriate) their username or client ID, group memberships, roles, tenants, mapping rules, and
 * associated claims.
 *
 * <p>Either {@code authenticatedUsername} or {@code authenticatedClientId} must be set, but not
 * both, unless the authentication represents an anonymous user {@code anonymousUser} in which case
 * both can be null.
 *
 * <p>Membership data (group IDs, role IDs, tenant IDs, and mapping rule IDs) may be loaded lazily.
 * For M2M bearer-token flows the memberships are deferred until first access; the four secondary
 * storage queries never run on broker-only paths that only need the principal identity. For
 * browser-session flows the memberships are always resolved eagerly so that the full auth is
 * available in session storage.
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonDeserialize(builder = CamundaAuthentication.Builder.class)
public final class CamundaAuthentication implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @JsonProperty("authenticated_username")
  private final String authenticatedUsername;

  @JsonProperty("authenticated_client_id")
  private final String authenticatedClientId;

  @JsonProperty("anonymous_user")
  private final boolean anonymousUser;

  @JsonProperty("claims")
  private final Map<String, Object> claims;

  /**
   * Resolved membership data; {@code null} until first access when a loader is present. Not
   * transient — survives Java serialization so that eagerly-resolved session auth remains complete
   * after cluster failover.
   */
  private volatile MembershipData membershipData;

  /**
   * Deferred loader for membership data. Marked {@code transient} so it is excluded from Java
   * serialization: a lambda cannot be serialized, and eager auths (which set {@code membershipData}
   * at construction) never need it after deserialization.
   */
  private transient MembershipLoader membershipLoader;

  private CamundaAuthentication(final Builder builder) {
    if (!builder.anonymous && builder.username != null && builder.clientId != null) {
      throw new IllegalArgumentException("Either username or clientId must be set, not both.");
    }
    this.authenticatedUsername = builder.username;
    this.authenticatedClientId = builder.clientId;
    this.anonymousUser = builder.anonymous;
    this.claims = builder.claims;
    if (builder.membershipLoader != null) {
      // Lazy path: defer the four DB queries until first membership access.
      this.membershipData = null;
      this.membershipLoader = builder.membershipLoader;
    } else {
      // Eager path: memberships are computed upfront (used for session-based auth).
      this.membershipData =
          new MembershipData(
              Collections.unmodifiableList(builder.groupIds),
              Collections.unmodifiableList(builder.roleIds),
              Collections.unmodifiableList(builder.tenants),
              Collections.unmodifiableList(builder.mappingRules));
      this.membershipLoader = null;
    }
  }

  // ── Record-compatible accessors ──────────────────────────────────────────────

  public String authenticatedUsername() {
    return authenticatedUsername;
  }

  public String authenticatedClientId() {
    return authenticatedClientId;
  }

  public boolean anonymousUser() {
    return anonymousUser;
  }

  public Map<String, Object> claims() {
    return claims;
  }

  @JsonProperty("authenticated_group_ids")
  public List<String> authenticatedGroupIds() {
    return getMembershipData().groupIds();
  }

  @JsonProperty("authenticated_role_ids")
  public List<String> authenticatedRoleIds() {
    return getMembershipData().roleIds();
  }

  @JsonProperty("authenticated_tenant_ids")
  public List<String> authenticatedTenantIds() {
    return getMembershipData().tenantIds();
  }

  @JsonProperty("authenticated_mapping_rule_ids")
  public List<String> authenticatedMappingRuleIds() {
    return getMembershipData().mappingRuleIds();
  }

  @JsonIgnore
  public boolean isAnonymous() {
    return anonymousUser;
  }

  // ── Factory methods ──────────────────────────────────────────────────────────

  public static CamundaAuthentication none() {
    return of(b -> b);
  }

  public static CamundaAuthentication anonymous() {
    return of(b -> b.anonymous(true));
  }

  public static CamundaAuthentication of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  // ── Lazy-load logic ──────────────────────────────────────────────────────────

  /**
   * Returns the membership data, triggering the deferred load on first access.
   *
   * <p>Uses double-checked locking with a {@code volatile} field, which is safe in Java 5+. If no
   * loader is present (eager construction or post-deserialization of an old lazy auth that was
   * never triggered), returns {@link MembershipData#EMPTY} so callers never receive {@code null}.
   */
  private MembershipData getMembershipData() {
    MembershipData data = membershipData;
    if (data == null) {
      synchronized (this) {
        data = membershipData;
        if (data == null) {
          data = membershipLoader != null ? membershipLoader.load() : MembershipData.EMPTY;
          membershipData = data;
        }
      }
    }
    return data;
  }

  // ── equals / hashCode / toString ─────────────────────────────────────────────

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CamundaAuthentication other)) {
      return false;
    }
    return anonymousUser == other.anonymousUser
        && Objects.equals(authenticatedUsername, other.authenticatedUsername)
        && Objects.equals(authenticatedClientId, other.authenticatedClientId)
        && Objects.equals(claims, other.claims)
        && Objects.equals(getMembershipData(), other.getMembershipData());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        authenticatedUsername,
        authenticatedClientId,
        anonymousUser,
        claims,
        getMembershipData());
  }

  @Override
  public String toString() {
    final MembershipData data = getMembershipData();
    return "CamundaAuthentication["
        + "authenticatedUsername="
        + authenticatedUsername
        + ", authenticatedClientId="
        + authenticatedClientId
        + ", anonymousUser="
        + anonymousUser
        + ", authenticatedGroupIds="
        + data.groupIds()
        + ", authenticatedRoleIds="
        + data.roleIds()
        + ", authenticatedTenantIds="
        + data.tenantIds()
        + ", authenticatedMappingRuleIds="
        + data.mappingRuleIds()
        + ", claims="
        + claims
        + ']';
  }

  // ── Inner types ───────────────────────────────────────────────────────────────

  /**
   * Holds the four resolved membership lists. Instances are immutable after construction. The
   * {@link #EMPTY} sentinel is returned when no loader is present and no data has been set (e.g.
   * after Java deserialization of an auth that was never accessed).
   */
  public record MembershipData(
      List<String> groupIds,
      List<String> roleIds,
      List<String> tenantIds,
      List<String> mappingRuleIds) {

    static final MembershipData EMPTY =
        new MembershipData(List.of(), List.of(), List.of(), List.of());
  }

  /**
   * Deferred supplier of {@link MembershipData}. Implementations run the four secondary-storage
   * queries (mapping rules → groups → roles → tenants) exactly once per {@link
   * CamundaAuthentication} instance.
   */
  @FunctionalInterface
  public interface MembershipLoader {
    MembershipData load();
  }

  // ── Builder ──────────────────────────────────────────────────────────────────

  @JsonPOJOBuilder(withPrefix = "")
  public static final class Builder {

    private String username;
    private String clientId;
    private boolean anonymous;
    private final List<String> groupIds = new ArrayList<>();
    private final List<String> roleIds = new ArrayList<>();
    private final List<String> tenants = new ArrayList<>();
    private final List<String> mappingRules = new ArrayList<>();
    private Map<String, Object> claims;
    private MembershipLoader membershipLoader;

    @JsonProperty("authenticated_username")
    public Builder user(final String value) {
      username = value;
      return this;
    }

    @JsonProperty("authenticated_client_id")
    public Builder clientId(final String value) {
      clientId = value;
      return this;
    }

    @JsonProperty("anonymous_user")
    public Builder anonymous(final boolean value) {
      anonymous = value;
      return this;
    }

    public Builder group(final String value) {
      return groupIds(Collections.singletonList(value));
    }

    @JsonProperty("authenticated_group_ids")
    public Builder groupIds(final List<String> values) {
      if (values != null) {
        groupIds.addAll(values);
      }
      return this;
    }

    public Builder role(final String value) {
      return roleIds(Collections.singletonList(value));
    }

    @JsonProperty("authenticated_role_ids")
    public Builder roleIds(final List<String> values) {
      if (values != null) {
        roleIds.addAll(values);
      }
      return this;
    }

    public Builder tenant(final String tenant) {
      return tenants(Collections.singletonList(tenant));
    }

    @JsonProperty("authenticated_tenant_ids")
    public Builder tenants(final List<String> values) {
      if (values != null) {
        tenants.addAll(values);
      }
      return this;
    }

    public Builder mappingRule(final String mappingRule) {
      return mappingRule(Collections.singletonList(mappingRule));
    }

    @JsonProperty("authenticated_mapping_rule_ids")
    public Builder mappingRule(final List<String> values) {
      if (values != null) {
        mappingRules.addAll(values);
      }
      return this;
    }

    @JsonProperty("claims")
    public Builder claims(final Map<String, Object> value) {
      claims = value;
      return this;
    }

    /**
     * Registers a deferred {@link MembershipLoader}. When set, the four membership lists are not
     * resolved at construction time but on the first call to any of the membership accessors
     * ({@code authenticatedGroupIds()}, etc.). This path is used for M2M bearer tokens that may
     * never need memberships (e.g. broker-only paths with authorization disabled).
     *
     * <p>Calling this method and also calling any of {@link #groupIds}, {@link #roleIds}, {@link
     * #tenants}, or {@link #mappingRule} in the same builder invocation is undefined behaviour; the
     * loader takes precedence.
     */
    public Builder lazyMemberships(final MembershipLoader loader) {
      membershipLoader = loader;
      return this;
    }

    public CamundaAuthentication build() {
      return new CamundaAuthentication(this);
    }
  }
}
