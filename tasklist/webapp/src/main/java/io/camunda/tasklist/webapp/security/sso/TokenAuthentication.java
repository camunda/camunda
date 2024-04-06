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

  public String getNewTokenByRefreshToken() {
    try {
      final TokenRequest tokenRequest = getAuthAPI().renewAuth(refreshToken);
      final TokenHolder tokenHolder = tokenRequest.execute();
      authenticate(
          tokenHolder.getIdToken(), tokenHolder.getRefreshToken(), tokenHolder.getAccessToken());
      getLogger().info("New tokens received and validated.");
      return accessToken;
    } catch (Auth0Exception e) {
      getLogger().error(e.getMessage(), e.getCause());
      setAuthenticated(false);
      return null;
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

  public String getOrganization() {
    return organization;
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
