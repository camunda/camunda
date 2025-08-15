/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.providers.MutualTlsAuthenticationProvider;
import io.camunda.authentication.service.CertificateUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentication filter for mutual TLS (mTLS) using client X.509 certificates. This filter extracts
 * client certificates from the request and creates authentication tokens for direct certificate
 * validation.
 */
public class MutualTlsAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(MutualTlsAuthenticationFilter.class);
  private static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

  private final MutualTlsAuthenticationProvider authenticationProvider;
  private final CertificateUserService certificateUserService;

  public MutualTlsAuthenticationFilter(
      final MutualTlsAuthenticationProvider authenticationProvider,
      final CertificateUserService certificateUserService) {
    this.authenticationProvider = authenticationProvider;
    this.certificateUserService = certificateUserService;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    // Check if already authenticated
    final Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
    if (existingAuth != null && existingAuth.isAuthenticated()) {
      LOG.debug("Request already authenticated, skipping mTLS authentication");
      filterChain.doFilter(request, response);
      return;
    }

    // Extract client certificate from request
    final X509Certificate[] certificates =
        (X509Certificate[]) request.getAttribute(CERTIFICATE_ATTRIBUTE);

    if (certificates == null || certificates.length == 0) {
      LOG.debug("No client certificates found for request");
      filterChain.doFilter(request, response);
      return;
    }

    try {
      // Use first certificate in chain (client certificate)
      final X509Certificate clientCertificate = certificates[0];
      LOG.info(
          "Found client certificate for mTLS authentication: Subject={}, Issuer={}",
          clientCertificate.getSubjectX500Principal().getName(),
          clientCertificate.getIssuerX500Principal().getName());

      // Create pre-authenticated token with certificate
      final PreAuthenticatedAuthenticationToken authToken =
          new PreAuthenticatedAuthenticationToken(clientCertificate, null);

      // Authenticate using provider
      final Authentication authentication = authenticationProvider.authenticate(authToken);

      if (authentication != null && authentication.isAuthenticated()) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        LOG.info(
            "Successfully authenticated mTLS request: {} - Principal: {}",
            request.getRequestURI(),
            authentication.getName());

        // Ensure user exists in the system for mTLS authentication
        if (certificateUserService != null) {
          try {
            final String username = authentication.getName();
            certificateUserService.ensureUserExists(username, clientCertificate);
            LOG.debug("Ensured mTLS user exists in system: {}", username);
          } catch (final Exception e) {
            LOG.error("Failed to ensure mTLS user exists: " + authentication.getName(), e);
            // Don't fail the authentication - just log the error
          }
        } else {
          LOG.warn(
              "CertificateUserService not available - mTLS user will not be created in system");
        }
      }

    } catch (final Exception e) {
      LOG.error("Failed to authenticate mTLS request for: " + request.getRequestURI(), e);
      response.sendError(
          HttpServletResponse.SC_UNAUTHORIZED,
          "Mutual TLS authentication failed: " + e.getMessage());
      return;
    }

    filterChain.doFilter(request, response);
  }
}
