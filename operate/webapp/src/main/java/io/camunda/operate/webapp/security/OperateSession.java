/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

public class OperateSession implements Session {

  private final MapSession delegate;

  private boolean changed;

  private boolean polling;

  public OperateSession(final String id) {
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

  public OperateSession setId(final String id) {
    delegate.setId(id);
    return this;
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

  public boolean containsAuthentication() {
    return getAuthentication() != null;
  }

  public boolean isAuthenticated() {
    final var authentication = getAuthentication();
    return (authentication != null && authentication.isAuthenticated());
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
    final OperateSession session = (OperateSession) o;
    return Objects.equals(getId(), session.getId());
  }

  @Override
  public String toString() {
    return String.format("OperateSession: %s ", getId());
  }

  @Override
  public Instant getLastAccessedTime() {
    return delegate.getLastAccessedTime();
  }

  public boolean isPolling() {
    return polling;
  }

  public OperateSession setPolling(final boolean polling) {
    this.polling = polling;
    return this;
  }

  @Override
  public void setLastAccessedTime(final Instant lastAccessedTime) {
    delegate.setLastAccessedTime(lastAccessedTime);
    changed = true;
  }

  @Override
  public Duration getMaxInactiveInterval() {
    return delegate.getMaxInactiveInterval();
  }

  @Override
  public void setMaxInactiveInterval(final Duration interval) {
    delegate.setMaxInactiveInterval(interval);
    changed = true;
  }

  @Override
  public boolean isExpired() {
    return delegate.isExpired();
  }
}
