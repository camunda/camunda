/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

  public Instant getLastAccessedTime() {
    return delegate.getLastAccessedTime();
  }

  public void setLastAccessedTime(final Instant lastAccessedTime) {
    delegate.setLastAccessedTime(lastAccessedTime);
    changed = true;
  }

  public Duration getMaxInactiveInterval() {
    return delegate.getMaxInactiveInterval();
  }

  @Override
  public void setMaxInactiveInterval(final Duration interval) {
    delegate.setMaxInactiveInterval(interval);
    changed = true;
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
