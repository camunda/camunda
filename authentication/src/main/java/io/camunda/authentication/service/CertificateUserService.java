/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.authentication.ConditionalOnCertificateAuthEnabled;
import io.camunda.authentication.config.MutualTlsProperties;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.entity.ClusterMetadata.AppName;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimAccessor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Service for handling certificate-based user authentication and user management. This service
 * supports multiple certificate authentication scenarios:
 *
 * <p>1. OIDC with JWT client assertions (MS Entra certificate-based OAuth2) 2. Direct mTLS
 * certificate authentication 3. Traditional OIDC flows
 *
 * <p>The service automatically creates user records for certificate-authenticated users and handles
 * role assignment based on explicitly configured roles. No automatic privilege escalation occurs.
 */
@Service
@ConditionalOnCertificateAuthEnabled
@ConditionalOnSecondaryStorageEnabled
@Profile("consolidated-auth")
public class CertificateUserService implements CamundaUserService {

  private static final Logger LOG = LoggerFactory.getLogger(CertificateUserService.class);
  private static final String SALES_PLAN_TYPE = "";

  private static final Map<AppName, String> C8_LINKS = Map.of();
  private static final String EMAILADDRESS = "EMAILADDRESS";
  private static final String EMAIL = "EMAIL";
  private static final String EMAIL_OID = "1.2.840.113549.1.9.1";

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantServices tenantServices;
  private final UserServices userServices;
  private final RoleServices roleServices;
  private final Optional<OAuth2AuthorizedClientRepository> authorizedClientRepository;
  private final Optional<HttpServletRequest> request;
  private final Optional<MutualTlsProperties> mtlsProperties;

  public CertificateUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final TenantServices tenantServices,
      final UserServices userServices,
      final RoleServices roleServices,
      final Optional<OAuth2AuthorizedClientRepository> authorizedClientRepository,
      final Optional<HttpServletRequest> request,
      final Optional<MutualTlsProperties> mtlsProperties) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantServices = tenantServices;
    this.userServices = userServices;
    this.roleServices = roleServices;
    this.authorizedClientRepository = authorizedClientRepository;
    this.request = request;
    this.mtlsProperties = mtlsProperties;
  }

  private Optional<OidcUser> getCamundaUser() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      final Object principal = auth.getPrincipal();
    }
    return Optional.ofNullable(auth)
        .map(Authentication::getPrincipal)
        .map(principal -> principal instanceof final OidcUser user ? user : null);
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(this::getCurrentUser)
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();

    // Check if it's an mTLS user
    if (isMtlsAuthentication(authentication)) {
      throw new UnsupportedOperationException("mTLS users do not have OAuth tokens");
    }

    final var oidcUser = getOidcUser(authentication);

    if (oidcUser == null) {
      throw new UnsupportedOperationException("User is not authenticated or is not a OIDC user");
    }

    return Optional.ofNullable(getToken(authentication, oidcUser))
        .map(Json::createValue)
        .map(JsonString::toString)
        .orElseThrow(() -> new UnsupportedOperationException("User does not have a valid token"));
  }

  protected String getToken(final Authentication authentication, final OidcUser oidcUser) {
    return Optional.ofNullable(getAccessToken(authentication))
        .orElseGet(() -> getIdToken(oidcUser));
  }

  protected String getAccessToken(final Authentication authentication) {
    return Optional.of(authentication)
        .map(OAuth2AuthenticationToken.class::cast)
        .map(this::getAuthorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(OAuth2AccessToken::getTokenValue)
        .orElse(null);
  }

  protected String getIdToken(final OidcUser oidcUser) {
    return Optional.of(oidcUser)
        .map(OidcUser::getIdToken)
        .map(OidcIdToken::getTokenValue)
        .orElse(null);
  }

  protected CamundaUserDTO getCurrentUser(final CamundaAuthentication authentication) {
    final var user = getUser();
    final var username = authentication.authenticatedUsername();
    final var groups = authentication.authenticatedGroupIds();
    final var roles = authentication.authenticatedRoleIds();
    final var tenants = getTenantsForCamundaAuthentication(authentication);
    final var authorizedApplications = getAuthorizedApplications(authentication);
    return new CamundaUserDTO(
        user.getFullName(),
        username,
        user.getEmail(),
        authorizedApplications,
        tenants,
        groups,
        roles,
        SALES_PLAN_TYPE,
        C8_LINKS,
        true);
  }

  protected StandardClaimAccessor getUser() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();

    // Check if it's mTLS certificate-based authentication
    if (isMtlsAuthentication(authentication)) {
      final Object principal = authentication.getPrincipal();
      if (principal instanceof X509Certificate) {
        final X509Certificate cert = (X509Certificate) principal;
        return new MtlsCertificateUser(cert);
      }
    }

    // Check if it's a certificate-based authentication (legacy)
    if (authentication
            instanceof
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken
        && ClientAssertionConstants.CERT_USER_ID.equals(authentication.getName())) {
      return new CertificateUser();
    }

    // Check if it's JWT-based authentication (from certificate OAuth2 flow)
    if (authentication instanceof JwtAuthenticationToken) {
      return new JwtBasedUser(((JwtAuthenticationToken) authentication).getToken());
    }

    return Optional.ofNullable(getOidcUser(authentication))
        .map(StandardClaimAccessor.class::cast)
        .orElseGet(() -> getOidcTokenBasedUser(authentication));
  }

  protected OidcUser getOidcUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .map(Authentication::getPrincipal)
        .filter(OidcUser.class::isInstance)
        .map(OidcUser.class::cast)
        .orElse(null);
  }

  protected StandardClaimAccessor getOidcTokenBasedUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(AbstractOAuth2TokenAuthenticationToken.class::isInstance)
        .map(AbstractOAuth2TokenAuthenticationToken.class::cast)
        .map(AbstractOAuth2TokenAuthenticationToken::getTokenAttributes)
        .map(OidcTokenUser::new)
        .orElse(null);
  }

  protected OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken) {
    if (authorizedClientRepository.isEmpty() || request.isEmpty()) {
      LOG.warn(
          "OAuth2AuthorizedClientRepository or HttpServletRequest not available for OIDC token retrieval");
      return null;
    }

    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return authorizedClientRepository
        .get()
        .loadAuthorizedClient(clientRegistrationId, authenticationToken, request.get());
  }

  protected List<String> getAuthorizedApplications(final CamundaAuthentication authentication) {
    final var applicationAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    return applicationAccess.allowed()
        ? applicationAccess.authorization().resourceIds()
        : List.of();
  }

  protected List<TenantEntity> getTenantsForCamundaAuthentication(
      final CamundaAuthentication authentication) {
    return Optional.ofNullable(authentication.authenticatedTenantIds())
        .filter(t -> !t.isEmpty())
        .map(this::getTenants)
        .orElseGet(List::of);
  }

  protected List<TenantEntity> getTenants(final List<String> tenantIds) {
    return tenantServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .search(TenantQuery.of(q -> q.filter(f -> f.tenantIds(tenantIds)).unlimited()))
        .items();
  }

  // === mTLS User Management Methods ===

  /**
   * Creates or ensures a user exists in the Camunda system for the certificate-authenticated user.
   * This is called automatically when mTLS authentication succeeds.
   */
  public void ensureUserExists(final String username, final X509Certificate certificate) {
    try {
      LOG.info("Ensuring user exists for mTLS certificate user: {}", username);

      // Check if user already exists
      try {
        final var existingUser =
            userServices.withAuthentication(CamundaAuthentication.anonymous()).getUser(username);

        if (existingUser != null) {
          LOG.info("User already exists: {}", username);
          return;
        }
      } catch (final ServiceException e) {
        // User doesn't exist, which is expected for new users
        LOG.debug("User {} doesn't exist yet, will create", username);
      }

      // Extract user information from certificate
      final String email = extractEmailFromCertificate(certificate);
      final String displayName = extractDisplayNameFromCertificate(certificate);

      // Create the user
      LOG.info("Creating new mTLS user: {} with email: {}", username, email);
      final var createUserRequest =
          new UserDTO(
              username, displayName, email, "" // No password needed for certificate auth
              );

      final var createdUser =
          userServices
              .withAuthentication(CamundaAuthentication.anonymous())
              .createUser(createUserRequest)
              .join();

      LOG.info("Created mTLS user: {}", createdUser.getUsername());

      // Assign only explicitly configured roles - never automatic admin escalation
      assignConfiguredRoles(username);

    } catch (final Exception e) {
      LOG.error("Failed to ensure user exists for mTLS certificate user: " + username, e);
      throw new RuntimeException("Failed to create mTLS user", e);
    }
  }

  private void assignConfiguredRoles(final String username) {
    try {
      // SECURITY: Only assign explicitly configured roles - never automatic admin escalation
      if (mtlsProperties.isPresent()) {
        final List<String> configuredRoles = mtlsProperties.get().getDefaultRoles();
        if (configuredRoles != null && !configuredRoles.isEmpty()) {
          LOG.info("Assigning configured mTLS roles to user '{}': {}", username, configuredRoles);
          for (final String role : configuredRoles) {
            final String roleId = mapRoleToId(role);
            if (roleId == null) {
              LOG.warn("Unknown role '{}' configured for mTLS users, skipping", role);
              continue;
            }

            // Security audit: Log admin role assignments
            if (isAdminRole(roleId)) {
              LOG.warn(
                  "SECURITY AUDIT: Assigning administrative role '{}' to mTLS user '{}'. "
                      + "Ensure this is explicitly configured and intended.",
                  roleId,
                  username);
            }

            // Assign role using proper authentication context
            final var membershipRequest = new RoleMemberRequest(roleId, username, EntityType.USER);
            roleServices
                .withAuthentication(CamundaAuthentication.anonymous())
                .addMember(membershipRequest)
                .join();

            LOG.info("Successfully assigned role '{}' to mTLS user: {}", roleId, username);
          }
        } else {
          LOG.info(
              "No roles configured for mTLS users, user '{}' will have minimal default access",
              username);
        }
      } else {
        LOG.info(
            "No mTLS properties available, user '{}' will have minimal default access", username);
      }
    } catch (final Exception e) {
      LOG.error(
          "Failed to assign configured roles to mTLS user '{}': {}", username, e.getMessage(), e);
      // Don't throw exception here - user creation succeeded, role assignment is secondary
    }
  }

  private String mapRoleToId(final String role) {
    // Map role strings to internal role IDs
    if (role == null) {
      return null;
    }

    final String normalizedRole = role.toUpperCase();
    switch (normalizedRole) {
      case "ROLE_ADMIN":
      case "ADMIN":
        return "admin";
      case "ROLE_USER":
      case "USER":
        return "user";
      default:
        // For custom roles, return as-is (assuming they exist in the system)
        return role;
    }
  }

  private boolean isAdminRole(final String roleId) {
    return DefaultRole.ADMIN.getId().equals(roleId)
        || "ROLE_ADMIN".equalsIgnoreCase(roleId)
        || roleId.toUpperCase().contains("ADMIN");
  }

  private String extractEmailFromCertificate(final X509Certificate certificate) {
    // Try to extract email from certificate subject DN
    final String subjectDn = certificate.getSubjectX500Principal().getName();

    // Try different DN formats that X500Principal might return
    try {
      final LdapName ldapName = new LdapName(subjectDn);
      for (final Rdn rdn : ldapName.getRdns()) {
        final String type = rdn.getType();
        if (EMAILADDRESS.equalsIgnoreCase(type)
            || EMAIL.equalsIgnoreCase(type)
            || EMAIL_OID.equals(type)) { // OID for emailAddress
          return rdn.getValue().toString();
        }
      }
    } catch (final InvalidNameException e) {
      LOG.warn("Failed to parse certificate subject DN for email: {}", subjectDn, e);
    }

    // Try simple regex parsing as fallback for different DN formats
    final String emailPattern =
        "(?i)(?:" + EMAILADDRESS + "|" + EMAIL + "|1\\.2\\.840\\.113549\\.1\\.9\\.1)=([^,]+)";
    final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(emailPattern);
    final java.util.regex.Matcher matcher = pattern.matcher(subjectDn);
    if (matcher.find()) {
      return matcher.group(1);
    }

    // Fallback to a generated email
    return extractUsernameFromCertificate(certificate) + "@certificate.local";
  }

  private String extractDisplayNameFromCertificate(final X509Certificate certificate) {
    final String subjectDn = certificate.getSubjectX500Principal().getName();

    try {
      final LdapName ldapName = new LdapName(subjectDn);
      for (final Rdn rdn : ldapName.getRdns()) {
        if ("CN".equalsIgnoreCase(rdn.getType())) {
          return rdn.getValue().toString();
        }
      }
    } catch (final InvalidNameException e) {
      LOG.warn("Failed to parse certificate subject DN for display name: {}", subjectDn, e);
    }

    // Fallback to subject DN
    return subjectDn;
  }

  private String extractUsernameFromCertificate(final X509Certificate certificate) {
    final String subjectDn = certificate.getSubjectX500Principal().getName();

    try {
      final LdapName ldapName = new LdapName(subjectDn);
      for (final Rdn rdn : ldapName.getRdns()) {
        if ("CN".equalsIgnoreCase(rdn.getType())) {
          return rdn.getValue().toString();
        }
      }
    } catch (final InvalidNameException e) {
      LOG.warn("Failed to parse certificate subject DN for username: {}", subjectDn, e);
    }

    // Fallback to entire subject DN
    return subjectDn;
  }

  private boolean isMtlsAuthentication(final Authentication authentication) {
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      final Object principal = authentication.getPrincipal();
      return principal instanceof X509Certificate;
    }
    return false;
  }

  // === Inner Classes ===

  record OidcTokenUser(Map<String, Object> claims) implements StandardClaimAccessor {

    @Override
    public Map<String, Object> getClaims() {
      return claims;
    }
  }

  static class CertificateUser implements StandardClaimAccessor {
    @Override
    public Map<String, Object> getClaims() {
      return Map.of(
          "name",
          ClientAssertionConstants.CERT_USER_NAME,
          "email",
          ClientAssertionConstants.CERT_USER_EMAIL,
          "given_name",
          "-",
          "family_name",
          "-");
    }

    @Override
    public String getFullName() {
      return ClientAssertionConstants.CERT_USER_NAME;
    }

    @Override
    public String getEmail() {
      return ClientAssertionConstants.CERT_USER_EMAIL;
    }
  }

  static class JwtBasedUser implements StandardClaimAccessor {
    private final org.springframework.security.oauth2.jwt.Jwt jwt;

    public JwtBasedUser(final org.springframework.security.oauth2.jwt.Jwt jwt) {
      this.jwt = jwt;
    }

    @Override
    public Map<String, Object> getClaims() {
      return jwt.getClaims();
    }

    @Override
    public String getFullName() {
      return jwt.getClaimAsString("name") != null
          ? jwt.getClaimAsString("name")
          : ClientAssertionConstants.CERT_USER_NAME;
    }

    @Override
    public String getEmail() {
      return jwt.getClaimAsString("email") != null
          ? jwt.getClaimAsString("email")
          : ClientAssertionConstants.CERT_USER_EMAIL;
    }
  }

  static class MtlsCertificateUser implements StandardClaimAccessor {
    private final X509Certificate certificate;

    public MtlsCertificateUser(final X509Certificate certificate) {
      this.certificate = certificate;
    }

    @Override
    public Map<String, Object> getClaims() {
      return Map.of(
          "name", getFullName(),
          "email", getEmail(),
          "given_name", "-",
          "family_name", "-",
          "sub", getSubject());
    }

    @Override
    public String getFullName() {
      return extractDisplayNameFromCert();
    }

    @Override
    public String getEmail() {
      return extractEmailFromCert();
    }

    public String getSubject() {
      return extractUsernameFromCert();
    }

    private String extractEmailFromCert() {
      final String subjectDn = certificate.getSubjectX500Principal().getName();

      try {
        final LdapName ldapName = new LdapName(subjectDn);
        for (final Rdn rdn : ldapName.getRdns()) {
          if (EMAILADDRESS.equalsIgnoreCase(rdn.getType())
              || EMAIL.equalsIgnoreCase(rdn.getType())) {
            return rdn.getValue().toString();
          }
        }
      } catch (final InvalidNameException e) {
        // Ignore parsing errors
      }

      return extractUsernameFromCert() + "@certificate.local";
    }

    private String extractDisplayNameFromCert() {
      final String subjectDn = certificate.getSubjectX500Principal().getName();

      try {
        final LdapName ldapName = new LdapName(subjectDn);
        for (final Rdn rdn : ldapName.getRdns()) {
          if ("CN".equalsIgnoreCase(rdn.getType())) {
            return rdn.getValue().toString();
          }
        }
      } catch (final InvalidNameException e) {
        // Ignore parsing errors
      }

      return subjectDn;
    }

    private String extractUsernameFromCert() {
      final String subjectDn = certificate.getSubjectX500Principal().getName();

      try {
        final LdapName ldapName = new LdapName(subjectDn);
        for (final Rdn rdn : ldapName.getRdns()) {
          if ("CN".equalsIgnoreCase(rdn.getType())) {
            return rdn.getValue().toString();
          }
        }
      } catch (final InvalidNameException e) {
        // Ignore parsing errors
      }

      return subjectDn;
    }
  }
}
