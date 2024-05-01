/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.security.record;

import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.AddressStandardClaim;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class IdentityOidcUser implements OidcUser {
  private final OidcUser user;
  private final List<GrantedAuthority> extraAuthorities;

  public IdentityOidcUser(final OidcUser user, final List<GrantedAuthority> extraAuthorities) {
    this.user = user;
    this.extraAuthorities = extraAuthorities;
  }

  @Override
  public Map<String, Object> getClaims() {
    return user.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return user.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return user.getIdToken();
  }

  @Override
  public <A> A getAttribute(final String name) {
    return user.getAttribute(name);
  }

  @Override
  public Map<String, Object> getAttributes() {
    return user.getAttributes();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    final List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.addAll(user.getAuthorities());
    authorities.addAll(extraAuthorities);
    return authorities;
  }

  @Override
  public String getName() {
    return user.getName();
  }

  @Override
  public URL getIssuer() {
    return user.getIssuer();
  }

  @Override
  public String getSubject() {
    return user.getSubject();
  }

  @Override
  public List<String> getAudience() {
    return user.getAudience();
  }

  @Override
  public Instant getExpiresAt() {
    return user.getExpiresAt();
  }

  @Override
  public Instant getIssuedAt() {
    return user.getIssuedAt();
  }

  @Override
  public Instant getAuthenticatedAt() {
    return user.getAuthenticatedAt();
  }

  @Override
  public String getNonce() {
    return user.getNonce();
  }

  @Override
  public String getAuthenticationContextClass() {
    return user.getAuthenticationContextClass();
  }

  @Override
  public List<String> getAuthenticationMethods() {
    return user.getAuthenticationMethods();
  }

  @Override
  public String getAuthorizedParty() {
    return user.getAuthorizedParty();
  }

  @Override
  public String getAccessTokenHash() {
    return user.getAccessTokenHash();
  }

  @Override
  public String getAuthorizationCodeHash() {
    return user.getAuthorizationCodeHash();
  }

  @Override
  public String getFullName() {
    return user.getFullName();
  }

  @Override
  public String getGivenName() {
    return user.getGivenName();
  }

  @Override
  public String getFamilyName() {
    return user.getFamilyName();
  }

  @Override
  public String getMiddleName() {
    return user.getMiddleName();
  }

  @Override
  public String getNickName() {
    return user.getNickName();
  }

  @Override
  public String getPreferredUsername() {
    return user.getPreferredUsername();
  }

  @Override
  public String getProfile() {
    return user.getProfile();
  }

  @Override
  public String getPicture() {
    return user.getPicture();
  }

  @Override
  public String getWebsite() {
    return user.getWebsite();
  }

  @Override
  public String getEmail() {
    return user.getEmail();
  }

  @Override
  public Boolean getEmailVerified() {
    return user.getEmailVerified();
  }

  @Override
  public String getGender() {
    return user.getGender();
  }

  @Override
  public String getBirthdate() {
    return user.getBirthdate();
  }

  @Override
  public String getZoneInfo() {
    return user.getZoneInfo();
  }

  @Override
  public String getLocale() {
    return user.getLocale();
  }

  @Override
  public String getPhoneNumber() {
    return user.getPhoneNumber();
  }

  @Override
  public Boolean getPhoneNumberVerified() {
    return user.getPhoneNumberVerified();
  }

  @Override
  public AddressStandardClaim getAddress() {
    return user.getAddress();
  }

  @Override
  public Instant getUpdatedAt() {
    return user.getUpdatedAt();
  }

  @Override
  public <T> T getClaim(final String claim) {
    return user.getClaim(claim);
  }

  @Override
  public boolean hasClaim(final String claim) {
    return user.hasClaim(claim);
  }

  @Override
  public String getClaimAsString(final String claim) {
    return user.getClaimAsString(claim);
  }

  @Override
  public Boolean getClaimAsBoolean(final String claim) {
    return user.getClaimAsBoolean(claim);
  }

  @Override
  public Instant getClaimAsInstant(final String claim) {
    return user.getClaimAsInstant(claim);
  }

  @Override
  public URL getClaimAsURL(final String claim) {
    return user.getClaimAsURL(claim);
  }

  @Override
  public Map<String, Object> getClaimAsMap(final String claim) {
    return user.getClaimAsMap(claim);
  }

  @Override
  public List<String> getClaimAsStringList(final String claim) {
    return user.getClaimAsStringList(claim);
  }
}
