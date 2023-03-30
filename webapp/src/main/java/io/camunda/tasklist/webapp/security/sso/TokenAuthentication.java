/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.sso;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.net.TokenRequest;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.OldUsernameAware;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

@Profile(TasklistProfileService.SSO_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class TokenAuthentication extends AbstractAuthenticationToken implements OldUsernameAware {

  public static final String ORGANIZATION_ID = "id";
  public static final String ROLES_KEY = "roles";

  private transient Logger logger = LoggerFactory.getLogger(this.getClass());

  @Value("${" + TasklistProperties.PREFIX + ".auth0.claimName}")
  private String claimName;

  @Value("${" + TasklistProperties.PREFIX + ".auth0.organization}")
  private String organization;

  @Value("${" + TasklistProperties.PREFIX + ".auth0.domain}")
  private String domain;

  @Value("${" + TasklistProperties.PREFIX + ".auth0.clientId}")
  private String clientId;

  @Value("${" + TasklistProperties.PREFIX + ".auth0.clientSecret}")
  private String clientSecret;

  private String idToken;
  private String refreshToken;

  private String accessToken;
  private String salesPlanType;

  private List<Permission> permissions = new ArrayList<>();

  public TokenAuthentication() {
    super(null);
  }

  private boolean isIdEqualsOrganization(final Map<String, String> orgs) {
    return orgs.containsKey("id") && orgs.get("id").equals(organization);
  }

  // Need this because this class will be serialized in session
  private Logger getLogger() {
    if (logger == null) {
      logger = LoggerFactory.getLogger(this.getClass());
    }
    return logger;
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public void addPermission(Permission permission) {
    this.permissions.add(permission);
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      getLogger().info("Tokens are expired");
      if (refreshToken == null) {
        setAuthenticated(false);
        getLogger().info("No refresh token available. Authentication is invalid.");
      } else {
        getLogger().info("Get a new tokens by using refresh token");
        getNewTokenByRefreshToken();
      }
    }
    return super.isAuthenticated();
  }

  private void getNewTokenByRefreshToken() {
    try {
      final TokenRequest tokenRequest = getAuthAPI().renewAuth(refreshToken);
      final TokenHolder tokenHolder = tokenRequest.execute();
      authenticate(
          tokenHolder.getIdToken(), tokenHolder.getRefreshToken(), tokenHolder.getAccessToken());
      getLogger().info("New tokens received and validated.");
    } catch (Auth0Exception e) {
      getLogger().error(e.getMessage(), e.getCause());
      setAuthenticated(false);
    }
  }

  private AuthAPI getAuthAPI() {
    return new AuthAPI(domain, clientId, clientSecret);
  }

  public boolean hasExpired() {
    final Date expires = getExpiresAt();
    return expires == null || expires.before(new Date());
  }

  public Date getExpiresAt() {
    return JWT.decode(idToken).getExpiresAt();
  }

  @Override
  public String getCredentials() {
    return JWT.decode(idToken).getToken();
  }

  @Override
  public Object getPrincipal() {
    return JWT.decode(idToken).getSubject();
  }

  public void authenticate(
      final String idToken, final String refreshToken, final String accessToken) {
    this.idToken = idToken;
    this.accessToken = accessToken;
    // Normally the refresh token will be issued only once
    // after first successfully getting the access token
    // ,so we need to avoid that the refreshToken will be overridden with null
    if (refreshToken != null) {
      this.refreshToken = refreshToken;
    }
    final Claim claim = JWT.decode(idToken).getClaim(claimName);
    tryAuthenticateAsListOfMaps(claim);
    if (!isAuthenticated()) {
      throw new InsufficientAuthenticationException(
          "No permission for tasklist - check your organization id");
    }
  }

  private void tryAuthenticateAsListOfMaps(final Claim claim) {
    try {
      final List<? extends Map> claims = claim.asList(Map.class);
      if (claims != null) {
        setAuthenticated(claims.stream().anyMatch(this::isIdEqualsOrganization));
      }
    } catch (JWTDecodeException e) {
      getLogger().debug("Read organization claim as list of maps failed.", e);
    }
  }

  /**
   * Gets the claims for this JWT token. <br>
   * For an ID token, claims represent user profile information such as the user's name, profile,
   * picture, etc. <br>
   *
   * @return a Map containing the claims of the token.
   * @see <a href="https://auth0.com/docs/tokens/id-token">ID Token Documentation</a>
   */
  public Map<String, Claim> getClaims() {
    return JWT.decode(idToken).getClaims();
  }

  public List<String> getRoles(final String organizationsKey) {
    try {
      final Map<String, Claim> claims = getClaims();
      return findRolesForOrganization(claims, organizationsKey, organization);
    } catch (Exception e) {
      getLogger().error("Could not get roles. Return empty roles list.", e);
    }
    return List.of();
  }

  private List<String> findRolesForOrganization(
      final Map<String, Claim> claims, final String organizationsKey, final String organization) {
    try {
      final List<Map> orgInfos = claims.get(organizationsKey).asList(Map.class);
      if (orgInfos != null) {
        final Optional<Map> orgInfo =
            orgInfos.stream()
                .filter(oi -> oi.get(ORGANIZATION_ID).equals(organization))
                .findFirst();
        if (orgInfo.isPresent()) {
          return (List<String>) orgInfo.get().get(ROLES_KEY);
        }
      }
    } catch (Exception e) {
      getLogger()
          .error(
              String.format(
                  "Couldn't extract roles for organization '%s' in JWT claims. Return empty roles list.",
                  organization),
              e);
    }
    return List.of();
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public void setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
  }

  public String getAccessToken() {
    return accessToken;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TokenAuthentication that = (TokenAuthentication) o;
    return claimName.equals(that.claimName)
        && organization.equals(that.organization)
        && domain.equals(that.domain)
        && clientId.equals(that.clientId)
        && clientSecret.equals(that.clientSecret)
        && idToken.equals(that.idToken)
        && Objects.equals(refreshToken, that.refreshToken)
        && Objects.equals(salesPlanType, that.salesPlanType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        claimName,
        organization,
        domain,
        clientId,
        clientSecret,
        idToken,
        refreshToken,
        salesPlanType);
  }

  @Override
  public String getOldName() {
    return getName();
  }
}
