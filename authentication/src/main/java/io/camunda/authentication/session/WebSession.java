/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

public class WebSession implements Session {

  private final MapSession delegate;
  private boolean changed;
  private boolean polling;

  public WebSession(final String sessionId) {
    delegate = new MapSession(sessionId);
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

  public WebSession setId(final String id) {
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

  public WebSession setCreationTime(final Instant creationTime) {
    delegate.setCreationTime(creationTime);
    changed = true;
    return this;
  }

  public boolean containsAuthentication() {
    return Optional.ofNullable(getAuthentication()).isPresent();
  }

  public boolean isAuthenticated() {
    return Optional.ofNullable(getAuthentication())
        .map(Authentication::isAuthenticated)
        .orElse(false);
  }

  private Authentication getAuthentication() {
    return Optional.ofNullable((SecurityContext) delegate.getAttribute(SPRING_SECURITY_CONTEXT_KEY))
        .map(SecurityContext::getAuthentication)
        .orElse(null);
  }

  public boolean isPolling() {
    return polling;
  }

  public WebSession setPolling(final boolean polling) {
    this.polling = polling;
    return this;
  }

  public boolean shouldBeDeleted() {
    return isExpired() || containsAuthentication() && !isAuthenticated();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    return delegate.equals(obj);
  }

  @Override
  public String toString() {
    return String.format("Web Session: %s ", getId());
  }

  @Override
  public void setLastAccessedTime(final Instant lastAccessedTime) {
    if (!polling) {
      delegate.setLastAccessedTime(lastAccessedTime);
      changed = true;
    }
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
  public Instant getLastAccessedTime() {
    return delegate.getLastAccessedTime();
  }

  @Override
  public boolean isExpired() {
    return getMaxInactiveInterval() != null && delegate.isExpired();
  }
}
