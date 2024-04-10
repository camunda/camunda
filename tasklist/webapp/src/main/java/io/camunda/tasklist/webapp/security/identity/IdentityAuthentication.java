/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.exception.TokenDecodeException;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.ElasticsearchSessionRepository;
import io.camunda.tasklist.webapp.security.OldUsernameAware;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.security.tenant.TenantAwareAuthentication;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class IdentityAuthentication extends AbstractAuthenticationToken
    implements OldUsernameAware, TenantAwareAuthentication {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthentication.class);
  private Tokens tokens;
  private String id;
  private String name;
  private String userDisplayName;
  private List<String> permissions;
  private String subject;
  private Date expires;
  private Date refreshTokenExpiresAt;
  private volatile List<TasklistTenant> tenants = Collections.emptyList();
  private IdentityAuthorization authorization;
  private List<String> groups;

  public IdentityAuthentication() {
    super(null);
  }

  @Override
  public String getCredentials() {
    return tokens.getAccessToken();
  }

  @Override
  public Object getPrincipal() {
    return subject;
  }

  @Override
  public List<TasklistTenant> getTenants() {
    if (CollectionUtils.isEmpty(tenants)) {
      synchronized (this) {
        if (CollectionUtils.isEmpty(tenants)) {
          retrieveTenants();
        }
      }
    }
    return tenants;
  }

  private void retrieveTenants() {
    if (getTasklistProperties().getMultiTenancy().isEnabled()) {
      try {
        final var accessToken = tokens.getAccessToken();
        final var identityTenants = getIdentity().tenants().forToken(accessToken);

        if (CollectionUtils.isNotEmpty(identityTenants)) {
          tenants =
              identityTenants.stream()
                  .map((t) -> new TasklistTenant(t.getTenantId(), t.getName()))
                  .sorted(TENANT_NAMES_COMPARATOR)
                  .toList();
        } else {
          tenants = List.of();
        }
      } catch (RestException ex) {
        LOGGER.warn("Unable to retrieve tenants from Identity. Error: " + ex.getMessage(), ex);
        tenants = List.of();
      }
    } else {
      tenants = List.of();
    }
  }

  public Tokens getTokens() {
    return tokens;
  }

  private boolean hasExpired() {
    return expires == null || expires.before(new Date());
  }

  private boolean hasRefreshTokenExpired() {
    try {
      LOGGER.info("Refresh token will expire at {}", refreshTokenExpiresAt);
      return refreshTokenExpiresAt == null || refreshTokenExpiresAt.before(new Date());
    } catch (final TokenDecodeException e) {
      LOGGER.info(
          "Refresh token is not a JWT and expire date can not be determined. Error message: {}",
          e.getMessage());
      return false;
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      LOGGER.info("Access token is expired");
      if (hasRefreshTokenExpired()) {
        LOGGER.info("No refresh token available. Authentication is invalid.");
        setAuthenticated(false);
        getIdentity().authentication().revokeToken(tokens.getRefreshToken());
        return false;
      } else {
        try {
          LOGGER.info("Get a new access token by using refresh token");
          renewAccessToken();
        } catch (Exception e) {
          LOGGER.error("Renewing access token failed with exception", e);
          setAuthenticated(false);
        }
      }
    }
    return super.isAuthenticated();
  }

  public String getId() {
    return id;
  }

  public List<Permission> getPermissions() {
    return permissions.stream()
        .map(PermissionConverter.getInstance()::convert)
        .collect(Collectors.toList());
  }

  public void authenticate(final Tokens tokens) {
    if (tokens != null) {
      this.tokens = tokens;
    }
    final AccessToken accessToken =
        getIdentity().authentication().verifyToken(this.tokens.getAccessToken());
    final UserDetails userDetails = accessToken.getUserDetails();
    id = userDetails.getId();
    name = retrieveName(userDetails);
    userDisplayName = retrieveUserDisplayName();
    permissions = accessToken.getPermissions();
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }

    try {
      final TasklistProperties props = getTasklistProperties();
      if (props.getIdentity().isResourcePermissionsEnabled()) {
        authorization =
            new IdentityAuthorization(
                getIdentity().authorizations().forToken(this.tokens.getAccessToken()));
      }
    } catch (io.camunda.identity.sdk.exception.InvalidConfigurationException ice) {
      LOGGER.debug(
          "Base URL is not provided so it's not possible to get authorizations from Identity");
    } catch (Exception e) {
      LOGGER.debug("Identity and Tasklist misconfiguration.");
    }
    subject = accessToken.getToken().getSubject();
    expires = accessToken.getToken().getExpiresAt();
    groups = accessToken.getUserDetails().getGroups();
    if (!isPolling()) {
      try {
        refreshTokenExpiresAt =
            getIdentity().authentication().decodeJWT(this.tokens.getRefreshToken()).getExpiresAt();
      } catch (TokenDecodeException e) {
        LOGGER.error(
            "Unable to decode refresh token {} with exception: {}",
            this.tokens.getRefreshToken(),
            e.getMessage());
      }
    }
    if (!hasExpired()) {
      setAuthenticated(true);
    } else {
      setAuthenticated(false);
    }
  }

  @NotNull
  private static TasklistProperties getTasklistProperties() {
    return SpringContextHolder.getBean(TasklistProperties.class);
  }

  private String retrieveName(final UserDetails userDetails) {
    return userDetails.getUsername().orElse(userDetails.getId());
  }

  private void renewAccessToken() {
    authenticate(renewTokens(tokens.getRefreshToken()));
  }

  private Tokens renewTokens(final String refreshToken) {
    return IdentityService.requestWithRetry(
        () -> getIdentity().authentication().renewToken(refreshToken));
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  public IdentityAuthentication setExpires(final Date expires) {
    this.expires = expires;
    return this;
  }

  public IdentityAuthentication setPermissions(final List<String> permissions) {
    this.permissions = permissions;
    return this;
  }

  public String getUserDisplayName() {
    return userDisplayName;
  }

  private String retrieveUserDisplayName() {
    return getIdentity()
        .authentication()
        .verifyToken(this.tokens.getAccessToken())
        .getUserDetails()
        .getName()
        .orElse(this.name);
  }

  public IdentityAuthentication setUserDisplayName(String userDisplayName) {
    this.userDisplayName = userDisplayName;
    return this;
  }

  @Override
  public String getOldName() {
    return getId();
  }

  public IdentityAuthorization getAuthorizations() {
    return authorization;
  }

  public IdentityAuthentication setAuthorizations(IdentityAuthorization authorization) {
    this.authorization = authorization;
    return this;
  }

  public List<String> getGroups() {
    return groups;
  }

  public IdentityAuthentication setGroups(List<String> groups) {
    this.groups = groups;
    return this;
  }

  private boolean isPolling() {
    final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      return RequestContextHolder.getRequestAttributes() != null
          && Boolean.TRUE.equals(
              Boolean.parseBoolean(
                  ((ServletRequestAttributes) requestAttributes)
                      .getRequest()
                      .getHeader(ElasticsearchSessionRepository.POLLING_HEADER)));
    } else {
      return false;
    }
  }
}
