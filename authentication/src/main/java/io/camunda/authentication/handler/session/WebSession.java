/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler.session;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

public class WebSession implements Session {

  public static final String ID = "id";
  public static final String CREATION_TIME = "creationTime";
  public static final String LAST_ACCESSED_TIME = "lastAccessedTime";
  public static final String MAX_INACTIVE_INTERVAL_IN_SECONDS = "maxInactiveIntervalInSeconds";
  public static final String ATTRIBUTES = "attributes";

  public static final String INDEX_NAME = "web-session";
  public static final String INDEX_VERSION = "1.1.0";

  private final MapSession delegate;

  private boolean changed;

  private boolean polling;

  public WebSession(final String id) {
    delegate = new MapSession(id);
    polling = false;
  }

  boolean isChanged() {
    return changed;
  }

  void clearChangeFlag() {
    changed = false;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public String changeSessionId() {
    final String newId = delegate.changeSessionId();
    changed = true;
    return newId;
  }

  @Override
  public <T> T getAttribute(final String attributeName) {
    return delegate.getAttribute(attributeName);
  }

  @Override
  public Set<String> getAttributeNames() {
    return delegate.getAttributeNames();
  }

  @Override
  public void setAttribute(final String attributeName, final Object attributeValue) {
    delegate.setAttribute(attributeName, attributeValue);
    changed = true;
  }

  @Override
  public void removeAttribute(final String attributeName) {
    delegate.removeAttribute(attributeName);
    changed = true;
  }

  @Override
  public Instant getCreationTime() {
    return delegate.getCreationTime();
  }

  public void setCreationTime(final Instant creationTime) {
    delegate.setCreationTime(creationTime);
    changed = true;
  }

  public WebSession setId(final String id) {
    delegate.setId(id);
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WebSession session = (WebSession) o;
    return Objects.equals(getId(), session.getId());
  }

  @Override
  public String toString() {
    return String.format("CamundaSession: %s ", getId());
  }

  public boolean containsAuthentication() {
    return getAuthentication() != null;
  }

  public boolean isAuthenticated() {
    final var authentication = getAuthentication();
    try {
      return authentication != null && authentication.isAuthenticated();
    } catch (final InsufficientAuthenticationException ex) {
      // TODO consider not throwing exceptions in authentication.isAuthenticated()
      return false;
    }
  }

  private Authentication getAuthentication() {
    final var securityContext =
        (SecurityContext) delegate.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
    final Authentication authentication;

    if (securityContext != null) {
      authentication = securityContext.getAuthentication();
    } else {
      authentication = null;
    }

    return authentication;
  }
  public boolean isPolling() {
    return polling;
  }
  public WebSession setPolling(final boolean polling) {
    this.polling = polling;
    return this;
  }@Override
  public void setLastAccessedTime(final Instant lastAccessedTime) {
    if (!polling) {
      delegate.setLastAccessedTime(lastAccessedTime);
      changed = true;
    }
  }

  public boolean shouldBeDeleted() {
    return isExpired() || containsAuthentication() && !isAuthenticated();
  }





  @Override
  public Instant getLastAccessedTime() {
    return delegate.getLastAccessedTime();
  }

  @Override
  public void setMaxInactiveInterval(final Duration interval) {
    delegate.setMaxInactiveInterval(interval);
    changed = true;
  }

  @Override
  public Duration getMaxInactiveInterval() {
    return delegate.getMaxInactiveInterval();
  }

  @Override
  public boolean isExpired() {
    return getMaxInactiveInterval() != null && delegate.isExpired();
  }


}
