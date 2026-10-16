/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.source;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * The SCIM sources ({@code camunda.scim.sources}) seen from the login side: which source a login
 * belongs to, and how a login is bound to the users that source provisioned. The sources are read
 * with the same properties as the SCIM gateway, which this module cannot depend on.
 *
 * <p>Inactive unless {@code camunda.scim.enabled=true}. Without configured sources there is one
 * implicit source {@value #DEFAULT_SOURCE_ID} without registration and binding claim.
 */
public final class LoginSources {

  public static final String DEFAULT_SOURCE_ID = "default";

  /**
   * Key under which the login source travels inside the claims map of the authentication, so that
   * it reaches the membership resolution and the broker request. It is always overwritten or
   * removed when the authentication is built, so a claim sent by an IdP under this name is never
   * trusted, and the broker request converter moves it out of the token claims.
   */
  public static final String LOGIN_SOURCE_CLAIM =
      BrokerRequestAuthorizationConverter.LOGIN_SOURCE_CLAIM;

  private static final LoginSources DISABLED = new LoginSources(false, List.of(), Map.of());

  /** A source as far as logins are concerned. */
  public record Source(
      String id, @Nullable String oidcRegistration, @Nullable String loginBindingClaim) {}

  /** Bindable shape of one {@code camunda.scim.sources[n]} entry; only what logins need. */
  public static final class SourceProperties {
    private String id;
    private String oidcRegistration;
    private String loginBindingClaim;

    public String getId() {
      return id;
    }

    public void setId(final String id) {
      this.id = id;
    }

    public String getOidcRegistration() {
      return oidcRegistration;
    }

    public void setOidcRegistration(final String oidcRegistration) {
      this.oidcRegistration = oidcRegistration;
    }

    public String getLoginBindingClaim() {
      return loginBindingClaim;
    }

    public void setLoginBindingClaim(final String loginBindingClaim) {
      this.loginBindingClaim = loginBindingClaim;
    }
  }

  private final boolean enabled;
  private final List<Source> sources;
  private final Map<String, String> registrationByIssuer;

  /**
   * @param registrationByIssuer the registration id of the provider per configured issuer URI
   */
  public LoginSources(
      final boolean enabled,
      final List<Source> sources,
      final Map<String, String> registrationByIssuer) {
    this.enabled = enabled;
    this.sources = List.copyOf(sources);
    this.registrationByIssuer = Map.copyOf(registrationByIssuer);
  }

  public static LoginSources disabled() {
    return DISABLED;
  }

  public static LoginSources from(final Environment environment) {
    final var binder = Binder.get(environment);
    if (!binder.bind("camunda.scim.enabled", Boolean.class).orElse(false)) {
      return DISABLED;
    }
    final List<Source> sources = new ArrayList<>();
    binder
        .bind("camunda.scim.sources", Bindable.listOf(SourceProperties.class))
        .orElse(List.of())
        .forEach(
            p ->
                sources.add(
                    new Source(
                        p.getId(),
                        blankToNull(p.getOidcRegistration()),
                        blankToNull(p.getLoginBindingClaim()))));
    if (sources.isEmpty()) {
      sources.add(new Source(DEFAULT_SOURCE_ID, null, null));
    }
    return new LoginSources(true, sources, issuers(binder));
  }

  private static Map<String, String> issuers(final Binder binder) {
    final var prefix = "camunda.security.authentication.";
    final Map<String, String> byIssuer = new LinkedHashMap<>();
    final var rootIssuer = binder.bind(prefix + "oidc.issuer-uri", String.class).orElse(null);
    if (rootIssuer != null && !rootIssuer.isBlank()) {
      byIssuer.put(rootIssuer, "oidc");
    }
    binder
        .bind(prefix + "providers.oidc", Bindable.mapOf(String.class, Object.class))
        .orElse(Map.of())
        .keySet()
        .forEach(
            id -> {
              final var issuer =
                  binder
                      .bind(prefix + "providers.oidc." + id + ".issuer-uri", String.class)
                      .orElse(null);
              if (issuer != null && !issuer.isBlank()) {
                byIssuer.putIfAbsent(issuer, id);
              }
            });
    return byIssuer;
  }

  public boolean enabled() {
    return enabled;
  }

  public Optional<Source> source(final @Nullable String id) {
    return sources.stream().filter(source -> source.id().equals(id)).findFirst();
  }

  /** The ids of the configured sources, or only {@value #DEFAULT_SOURCE_ID}. */
  public List<String> ids() {
    return sources.stream().map(Source::id).toList();
  }

  /**
   * The source of a login through the given OIDC registration: the source configured for that
   * registration, else the only source without a registration, else none.
   */
  public @Nullable String sourceOfRegistration(final @Nullable String registrationId) {
    if (!enabled) {
      return null;
    }
    if (registrationId != null) {
      for (final var source : sources) {
        if (registrationId.equals(source.oidcRegistration())) {
          return source.id();
        }
      }
    }
    final var unbound = sources.stream().filter(s -> s.oidcRegistration() == null).toList();
    return unbound.size() == 1 ? unbound.getFirst().id() : null;
  }

  /** The source of a bearer token, through the provider that issued it. */
  public @Nullable String sourceOfIssuer(final @Nullable String issuer) {
    return sourceOfRegistration(issuer == null ? null : registrationByIssuer.get(issuer));
  }

  /**
   * Copy of the claims with the login source set to {@code source}, or removed if null. Used while
   * building the authentication, so a token cannot bring its own login source.
   */
  public static Map<String, Object> withLoginSource(
      final Map<String, Object> claims, final @Nullable String source) {
    final var copy = new HashMap<>(claims);
    copy.remove(LOGIN_SOURCE_CLAIM);
    if (source != null) {
      copy.put(LOGIN_SOURCE_CLAIM, source);
    }
    return copy;
  }

  /** The login source of an authentication's claims, if the web layer set one. */
  public static @Nullable String loginSource(final @Nullable Map<String, Object> claims) {
    return claims != null
            && claims.get(LOGIN_SOURCE_CLAIM) instanceof final String s
            && !s.isBlank()
        ? s
        : null;
  }

  /** The claims without the login source, as the claims of the token itself. */
  public static Map<String, Object> withoutLoginSource(final Map<String, Object> claims) {
    if (!claims.containsKey(LOGIN_SOURCE_CLAIM)) {
      return claims;
    }
    final var copy = new HashMap<>(claims);
    copy.remove(LOGIN_SOURCE_CLAIM);
    return copy;
  }

  private static @Nullable String blankToNull(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
