/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.providers;

import io.camunda.authentication.config.MutualTlsProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Authentication provider for mutual TLS using X.509 client certificates. Validates client
 * certificates and extracts user information and authorities.
 */
public class MutualTlsAuthenticationProvider implements AuthenticationProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MutualTlsAuthenticationProvider.class);
  private static final String DEFAULT_ROLE = "ROLE_USER";

  private final MutualTlsProperties mtlsProperties;

  public MutualTlsAuthenticationProvider(final MutualTlsProperties mtlsProperties) {
    this.mtlsProperties = mtlsProperties;
  }

  @Override
  public Authentication authenticate(final Authentication authentication)
      throws AuthenticationException {
    if (!(authentication instanceof PreAuthenticatedAuthenticationToken)) {
      return null;
    }

    final Object principal = authentication.getPrincipal();
    if (!(principal instanceof X509Certificate)) {
      LOG.warn("Principal is not an X509Certificate: {}", principal.getClass().getSimpleName());
      return null;
    }

    final X509Certificate certificate = (X509Certificate) principal;

    try {
      // Validate certificate
      validateCertificate(certificate);

      // Extract user information from certificate
      final String username = extractUsername(certificate);
      final List<SimpleGrantedAuthority> authorities = extractAuthorities(certificate);

      LOG.info(
          "Successfully validated certificate for user: {} with authorities: {}",
          username,
          authorities);

      return new PreAuthenticatedAuthenticationToken(username, certificate, authorities);

    } catch (final Exception e) {
      LOG.error(
          "Certificate validation failed for certificate: {} - Exception type: {} - Message: {}",
          certificate.getSubjectX500Principal().getName(),
          e.getClass().getSimpleName(),
          e.getMessage(),
          e);
      throw new BadCredentialsException("Certificate validation failed: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean supports(final Class<?> authentication) {
    return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
  }

  private void validateCertificate(final X509Certificate certificate) throws CertificateException {
    // Check certificate validity period
    certificate.checkValidity();

    final boolean isSelfSignedCert = isSelfSigned(certificate);
    final boolean allowSelfSigned = mtlsProperties.isAllowSelfSigned();

    LOG.info(
        "Certificate validation - Subject: {}, Self-signed: {}, Allow self-signed: {}",
        certificate.getSubjectX500Principal().getName(),
        isSelfSignedCert,
        allowSelfSigned);

    // Allow self-signed certificates if explicitly enabled
    if (allowSelfSigned && isSelfSignedCert) {
      LOG.info(
          "Accepting self-signed certificate (allowed by configuration): {}",
          certificate.getSubjectX500Principal().getName());
      return;
    }

    // For non-self-signed certificates, require trusted CA validation
    if (mtlsProperties.getTrustedCertificates() == null
        || mtlsProperties.getTrustedCertificates().isEmpty()) {
      LOG.error(
          "No trusted CAs configured for mTLS certificate validation: {}",
          certificate.getSubjectX500Principal().getName());
      throw new CertificateException(
          "No trusted Certificate Authorities configured. Cannot validate certificate authenticity. "
              + "Configure 'camunda.security.authentication.mtls.trusted-certificates' property "
              + "or enable 'camunda.security.authentication.mtls.allow-self-signed' for development.");
    }

    // Validate against trusted CA
    validateAgainstTrustedCAs(certificate);

    LOG.debug(
        "Certificate validation passed for: {}", certificate.getSubjectX500Principal().getName());
  }

  private void validateAgainstTrustedCAs(final X509Certificate certificate)
      throws CertificateException {
    try {
      final Set<TrustAnchor> trustAnchors = loadTrustedCAs();

      // Create certificate path with the client certificate
      final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      final List<X509Certificate> certList = Collections.singletonList(certificate);
      final CertPath certPath = certFactory.generateCertPath(certList);

      // Set up PKIX parameters
      final PKIXParameters pkixParams = new PKIXParameters(trustAnchors);
      pkixParams.setRevocationEnabled(false); // Disable CRL/OCSP for now - can be enabled later

      // Validate the certificate path
      final CertPathValidator validator = CertPathValidator.getInstance("PKIX");
      validator.validate(certPath, pkixParams);

      LOG.info(
          "Certificate successfully validated against trusted CAs: {}",
          certificate.getSubjectX500Principal().getName());

    } catch (final Exception e) {
      LOG.error(
          "Certificate validation failed against trusted CAs for: {} - Error: {}",
          certificate.getSubjectX500Principal().getName(),
          e.getMessage());
      throw new CertificateException(
          "Certificate validation failed against trusted Certificate Authorities: "
              + e.getMessage(),
          e);
    }
  }

  private Set<TrustAnchor> loadTrustedCAs() throws CertificateException {
    final Set<TrustAnchor> trustAnchors = new HashSet<>();

    try {
      final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

      for (final String trustedCertPath : mtlsProperties.getTrustedCertificates()) {
        try {
          // Load certificate from file path or classpath
          final X509Certificate trustedCert = loadCertificateFromPath(trustedCertPath, certFactory);
          trustAnchors.add(new TrustAnchor(trustedCert, null));
          LOG.debug(
              "Loaded trusted CA certificate: {}", trustedCert.getSubjectX500Principal().getName());
        } catch (final Exception e) {
          LOG.error(
              "Failed to load trusted CA certificate from path: {} - Error: {}",
              trustedCertPath,
              e.getMessage());
          throw new CertificateException(
              "Failed to load trusted CA certificate: " + trustedCertPath, e);
        }
      }

      if (trustAnchors.isEmpty()) {
        throw new CertificateException("No valid trusted CA certificates could be loaded");
      }

      LOG.info("Loaded {} trusted CA certificate(s) for mTLS validation", trustAnchors.size());
      return trustAnchors;

    } catch (final CertificateException e) {
      throw e;
    } catch (final Exception e) {
      throw new CertificateException("Failed to initialize trusted CA certificates", e);
    }
  }

  private X509Certificate loadCertificateFromPath(
      final String certPath, final CertificateFactory certFactory) throws Exception {
    if (certPath.startsWith("-----BEGIN CERTIFICATE-----")) {
      // Handle PEM format certificate content directly
      final ByteArrayInputStream certStream = new ByteArrayInputStream(certPath.getBytes());
      return (X509Certificate) certFactory.generateCertificate(certStream);
    } else {
      // Handle file path loading
      try {
        final Path filePath = Paths.get(certPath);
        if (!Files.exists(filePath)) {
          throw new CertificateException("Certificate file does not exist: " + certPath);
        }

        // Read file content
        final byte[] certBytes = Files.readAllBytes(filePath);
        final String certContent = new String(certBytes);

        // Validate PEM format
        if (!certContent.contains("-----BEGIN CERTIFICATE-----")
            || !certContent.contains("-----END CERTIFICATE-----")) {
          throw new CertificateException(
              "Certificate file is not in PEM format (missing BEGIN/END CERTIFICATE markers): "
                  + certPath);
        }

        // Parse certificate from file content
        final ByteArrayInputStream certStream = new ByteArrayInputStream(certBytes);
        final X509Certificate certificate =
            (X509Certificate) certFactory.generateCertificate(certStream);

        LOG.debug(
            "Successfully loaded certificate from file: {} - Subject: {}",
            certPath,
            certificate.getSubjectX500Principal().getName());
        return certificate;

      } catch (final IOException e) {
        throw new CertificateException("Failed to read certificate file: " + certPath, e);
      }
    }
  }

  private boolean isSelfSigned(final X509Certificate certificate) {
    try {
      // A certificate is self-signed if the subject and issuer are the same
      // and it can be verified with its own public key
      final boolean sameSubjectIssuer =
          certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());

      LOG.debug(
          "Self-signed check - Subject: {}, Issuer: {}, Same: {}",
          certificate.getSubjectX500Principal().getName(),
          certificate.getIssuerX500Principal().getName(),
          sameSubjectIssuer);

      if (!sameSubjectIssuer) {
        return false;
      }

      // Try to verify the certificate with its own public key
      certificate.verify(certificate.getPublicKey());
      LOG.debug("Certificate successfully verified with its own public key - it is self-signed");
      return true;

    } catch (final Exception e) {
      // If verification fails, it's not self-signed (or it's invalid)
      LOG.debug("Certificate verification with own public key failed: {}", e.getMessage());
      return false;
    }
  }

  private String extractUsername(final X509Certificate certificate) {
    final String subjectDn = certificate.getSubjectX500Principal().getName();

    // Try to extract CN (Common Name) from subject DN
    try {
      final LdapName ldapName = new LdapName(subjectDn);
      for (final Rdn rdn : ldapName.getRdns()) {
        if ("CN".equalsIgnoreCase(rdn.getType())) {
          return rdn.getValue().toString();
        }
      }
    } catch (final InvalidNameException e) {
      LOG.warn("Failed to parse subject DN: {}", subjectDn, e);
    }

    // Fallback to using the entire subject DN
    LOG.info("Using full subject DN as username: {}", subjectDn);
    return subjectDn;
  }

  private List<SimpleGrantedAuthority> extractAuthorities(final X509Certificate certificate) {
    final List<SimpleGrantedAuthority> authorities = new ArrayList<>();

    // SECURITY: Only assign minimal default role for certificate authentication
    // Additional roles must be granted through proper role management
    authorities.add(new SimpleGrantedAuthority(DEFAULT_ROLE));

    // Add configured default roles for certificate users
    if (mtlsProperties.getDefaultRoles() != null) {
      for (final String role : mtlsProperties.getDefaultRoles()) {
        final String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        authorities.add(new SimpleGrantedAuthority(roleWithPrefix));

        // Log admin role assignment for security audit
        if (roleWithPrefix.toUpperCase().contains("ADMIN")
            || roleWithPrefix.toUpperCase().contains("SUPER")) {
          LOG.warn(
              "SECURITY AUDIT: Assigning administrative role '{}' to certificate user. "
                  + "Ensure this is intended and properly configured.",
              roleWithPrefix);
        }
      }
    }

    return authorities;
  }
}
