/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import io.camunda.authentication.utils.OutageLog;
import io.camunda.search.entities.UserEntity;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.service.MappingRuleServices.SourcedAttributes;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.registry.ServiceRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cached lookup of the user stored in secondary storage, answering whether the user may act and
 * which attributes it carries.
 *
 * <p>Decides whether a user may act, based on the {@code active} flag stored with the user in
 * secondary storage. Results are cached per physical tenant and username for a fixed TTL, so a
 * deactivation takes effect after at most the TTL plus the exporter lag.
 *
 * <p>Users that are not stored (OIDC users need not be provisioned) and users whose {@code active}
 * flag was never written are active. A failing lookup also yields active (fail open), so an
 * unavailable secondary storage does not lock out every OIDC user; the failure is logged once per
 * outage and the open result is cached for the TTL like any other.
 *
 * <p>The same cached lookup serves the stored {@code attributes} that user-attribute mapping rules
 * match against. Where no user is found or the lookup fails the attributes are empty, so attribute
 * rules match nothing (fail closed for grants). Likewise the attributes of the groups the user is a
 * direct member of are served for group-attribute rules; a failing group lookup yields none.
 */
public final class ActiveUserChecker {

  private static final Logger LOG = LoggerFactory.getLogger(ActiveUserChecker.class);
  private static final int MAX_ENTRIES = 10_000;

  private record Key(String physicalTenantId, String username) {}

  /**
   * What is known about a user: absent or unreadable users are active with no attributes and no
   * source. {@code source} is the id of the SCIM source that provisioned the user, null if none
   * did; {@code groups} are the non-empty attributes of the user's direct groups with the source of
   * each group.
   */
  public record StoredUser(
      boolean active,
      @Nullable String externalId,
      @Nullable String source,
      Map<String, Object> attributes,
      List<SourcedAttributes> groups) {

    public static final StoredUser UNKNOWN = new StoredUser(true, null, null, Map.of(), List.of());

    StoredUser withGroups(final List<SourcedAttributes> groups) {
      return new StoredUser(active, externalId, source, attributes, groups);
    }

    /** Whether the user was provisioned by a SCIM source. */
    public boolean provisioned() {
      return source != null && !source.isBlank();
    }
  }

  private record Entry(StoredUser user, long expiresAtNanos) {}

  private final Supplier<ServiceRegistry> serviceRegistry;
  private final long ttlNanos;
  private final LongSupplier nanoTime;
  private final OutageLog outageLog = new OutageLog(LOG);
  private final ConcurrentHashMap<Key, Entry> cache = new ConcurrentHashMap<>();

  public ActiveUserChecker(final Supplier<ServiceRegistry> serviceRegistry, final Duration ttl) {
    this(serviceRegistry, ttl, System::nanoTime);
  }

  ActiveUserChecker(
      final Supplier<ServiceRegistry> serviceRegistry,
      final Duration ttl,
      final LongSupplier nanoTime) {
    this.serviceRegistry = serviceRegistry;
    ttlNanos = ttl.toNanos();
    this.nanoTime = nanoTime;
  }

  public boolean isActive(final String physicalTenantId, final String username) {
    return user(physicalTenantId, username).active();
  }

  public StoredUser user(final String physicalTenantId, final String username) {
    final var key = new Key(physicalTenantId, username);
    final long now = nanoTime.getAsLong();
    final var cached = cache.get(key);
    if (cached != null && now - cached.expiresAtNanos() < 0) {
      return cached.user();
    }
    final var user =
        lookup(physicalTenantId, username).withGroups(lookupGroups(physicalTenantId, username));
    if (ttlNanos > 0) {
      if (cache.size() >= MAX_ENTRIES) {
        evict(now);
      }
      cache.put(key, new Entry(user, now + ttlNanos));
    }
    return user;
  }

  private StoredUser lookup(final String physicalTenantId, final String username) {
    try {
      final UserEntity user =
          serviceRegistry
              .get()
              .userServices(physicalTenantId)
              .getUser(username, CamundaAuthentication.anonymous());
      outageLog.recovery("Active-user lookup recovered");
      LOG.debug(
          "Active-user lookup for '{}' found active={}",
          username,
          user == null ? null : user.active());
      if (user == null) {
        return StoredUser.UNKNOWN;
      }
      return new StoredUser(
          !Boolean.FALSE.equals(user.active()),
          user.externalId(),
          user.source(),
          user.attributes() == null ? Map.of() : user.attributes(),
          List.of());
    } catch (final ServiceException e) {
      if (e.getStatus() == ServiceException.Status.NOT_FOUND) {
        outageLog.recovery("Active-user lookup recovered");
        LOG.debug("Active-user lookup for '{}' found no user", username);
        return StoredUser.UNKNOWN;
      }
      return failOpen(username, e);
    } catch (final RuntimeException e) {
      return failOpen(username, e);
    }
  }

  /** Non-empty attributes of the groups the user is a direct member of; empty on failure. */
  private List<SourcedAttributes> lookupGroups(
      final String physicalTenantId, final String username) {
    try {
      return serviceRegistry
          .get()
          .groupServices(physicalTenantId)
          .getGroupsByMemberTypeAndMemberIds(
              Map.of(EntityType.USER, Set.of(username)), CamundaAuthentication.anonymous())
          .stream()
          .filter(group -> group.attributes() != null && !group.attributes().isEmpty())
          .map(group -> new SourcedAttributes(group.source(), group.attributes()))
          .toList();
    } catch (final RuntimeException e) {
      LOG.debug("Group-attribute lookup for '{}' failed: {}", username, e.toString());
      return List.of();
    }
  }

  private StoredUser failOpen(final String username, final RuntimeException e) {
    outageLog.failure(
        "Could not look up whether user '{}' is active; allowing the request: {}",
        username,
        e.toString());
    return StoredUser.UNKNOWN;
  }

  private void evict(final long now) {
    cache.values().removeIf(entry -> now - entry.expiresAtNanos() >= 0);
    if (cache.size() >= MAX_ENTRIES) {
      cache.clear();
    }
  }
}
