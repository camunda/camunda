/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

public class OperateSession implements Session {

    private final MapSession delegate;

    private boolean changed;

    public OperateSession(String id) {
        delegate = new MapSession(id);
    }

    boolean isChanged() {
        return changed;
    }

    void clearChangeFlag() {
        changed = false;
    }

    public String getId() {
        return delegate.getId();
    }

    public OperateSession setId(String id) {
        delegate.setId(id);
        return this;
    }

    public <T> T getAttribute(String attributeName) {
        return delegate.getAttribute(attributeName);
    }

    public Set<String> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    public void setAttribute(String attributeName, Object attributeValue) {
        delegate.setAttribute(attributeName, attributeValue);
        changed = true;
    }

    public void removeAttribute(String attributeName) {
        delegate.removeAttribute(attributeName);
        changed = true;
    }

    public Instant getCreationTime() {
        return delegate.getCreationTime();
    }

    public void setCreationTime(final Instant creationTime) {
        delegate.setCreationTime(creationTime);
        changed = true;
    }

    public void setLastAccessedTime(final Instant lastAccessedTime) {
        delegate.setLastAccessedTime(lastAccessedTime);
        changed = true;
    }

    public Instant getLastAccessedTime() {
        return delegate.getLastAccessedTime();
    }

    @Override
    public void setMaxInactiveInterval(final Duration interval) {
        delegate.setMaxInactiveInterval(interval);
        changed = true;
    }

    public Duration getMaxInactiveInterval() {
        return delegate.getMaxInactiveInterval();
    }

    public boolean isExpired() {
        return delegate.isExpired();
    }

    public boolean containsAuthentication() {
        return getAuthentication() != null;
    }

    public boolean isAuthenticated() {
        final var authentication = getAuthentication();
        return (authentication != null && authentication.isAuthenticated());
    }

    private Authentication getAuthentication() {
        final var securityContext = (SecurityContext) delegate.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
        final Authentication authentication;

        if (securityContext != null) {
            authentication = securityContext.getAuthentication();
        } else {
            authentication = null;
        }

        return authentication;
    }

    @Override
    public String changeSessionId() {
        final String newId = delegate.changeSessionId();
        changed = true;
        return newId;
    }

    @Override
    public String toString() {
        return String.format("OperateSession: %s ", getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OperateSession session = (OperateSession) o;
        return Objects.equals(getId(), session.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
