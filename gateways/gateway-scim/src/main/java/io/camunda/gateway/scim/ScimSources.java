/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import com.unboundid.scim2.common.exceptions.ScimException;
import io.camunda.gateway.scim.ScimAttributeProperties.SourceProperties;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The configured SCIM sources ({@code camunda.scim.sources}). A source ties a SCIM client to the
 * users and groups it owns. Without configuration there is one implicit source {@value
 * #DEFAULT_SOURCE_ID} that accepts any client and adopts unowned entities.
 */
public final class ScimSources {

  public static final String DEFAULT_SOURCE_ID = "default";

  private static final Logger LOG = LoggerFactory.getLogger(ScimSources.class);
  private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

  private final Map<String, Source> byId = new HashMap<>();
  private final List<Source> ordered = new ArrayList<>();

  /**
   * @throws IllegalStateException if the configuration violates the rules for sources
   */
  public ScimSources(final List<SourceProperties> configured) {
    final var properties = configured == null ? List.<SourceProperties>of() : configured;
    if (properties.isEmpty()) {
      add(new Source(DEFAULT_SOURCE_ID, Set.of(), null, null, true));
      return;
    }
    final boolean multiple = properties.size() > 1;
    final Set<String> clients = new HashSet<>();
    final Set<String> registrations = new HashSet<>();
    for (final var source : properties) {
      final var id = source.getId();
      if (id == null || !ID_PATTERN.matcher(id).matches()) {
        throw invalid("source id '" + id + "' must match " + ID_PATTERN.pattern());
      }
      if (byId.containsKey(id)) {
        throw invalid("source id '" + id + "' is configured more than once");
      }
      final var clientIds = nonBlank(source.getClientIds());
      final var registration = blankToNull(source.getOidcRegistration());
      if (multiple && clientIds.isEmpty()) {
        throw invalid("source '" + id + "' needs client-ids when several sources are configured");
      }
      if (multiple && registration == null) {
        throw invalid(
            "source '" + id + "' needs oidc-registration when several sources are configured");
      }
      for (final var client : clientIds) {
        if (!clients.add(client)) {
          throw invalid("client id '" + client + "' is configured for more than one source");
        }
      }
      if (registration != null && !registrations.add(registration)) {
        throw invalid("oidc-registration '" + registration + "' is used by more than one source");
      }
      final var claim = blankToNull(source.getLoginBindingClaim());
      if (registration == null && claim == null) {
        LOG.warn(
            "SCIM source '{}' sets neither oidc-registration nor login-binding-claim: a login"
                + " through any OIDC registration with a matching username is treated as the"
                + " provisioned user. Set one of them to bind logins to this source.",
            id);
      }
      add(new Source(id, Set.copyOf(clientIds), registration, claim, source.isAdoptUnowned()));
    }
  }

  /** The source that owns what the given SCIM client pushes, if the client is accepted. */
  public Optional<Source> resolve(final String clientId) {
    if (ordered.size() == 1 && ordered.getFirst().clientIds().isEmpty()) {
      return Optional.of(ordered.getFirst());
    }
    if (clientId == null) {
      return Optional.empty();
    }
    return ordered.stream().filter(source -> source.clientIds().contains(clientId)).findFirst();
  }

  /**
   * Resolves the source of the authenticated SCIM client.
   *
   * @throws ScimException with status 403 if the client is not accepted by any source
   */
  public Source require(final CamundaAuthentication authentication) throws ScimException {
    return resolve(authentication.authenticatedClientId())
        .orElseThrow(
            () -> new ScimException(403, null, "The client is not configured as a SCIM source"));
  }

  public Optional<Source> get(final String id) {
    return Optional.ofNullable(byId.get(id));
  }

  public List<String> ids() {
    return ordered.stream().map(Source::id).toList();
  }

  private void add(final Source source) {
    byId.put(source.id(), source);
    ordered.add(source);
  }

  private static List<String> nonBlank(final List<String> values) {
    return values == null
        ? List.of()
        : values.stream().filter(value -> value != null && !value.isBlank()).toList();
  }

  private static String blankToNull(final String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static IllegalStateException invalid(final String message) {
    return new IllegalStateException("Invalid camunda.scim.sources: " + message);
  }

  /**
   * A resolved source. Empty {@code clientIds} accepts any client; a null registration or claim
   * means the login is not restricted by it.
   */
  public record Source(
      String id,
      Set<String> clientIds,
      String oidcRegistration,
      String loginBindingClaim,
      boolean adoptUnowned) {

    /**
     * Whether an entity of the given owner is visible to this source: its own entities, and unowned
     * ones only if the source adopts them.
     */
    public boolean canSee(final String entitySource) {
      if (entitySource == null || entitySource.isEmpty()) {
        return adoptUnowned;
      }
      return id.equals(entitySource);
    }
  }
}
