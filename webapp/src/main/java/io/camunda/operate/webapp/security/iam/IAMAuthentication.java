/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.authentication.Tokens;
import io.camunda.iam.sdk.authentication.UserInfo;
import io.camunda.iam.sdk.authentication.dto.AuthCodeDto;
import io.camunda.iam.sdk.authentication.exception.TokenVerificationException;
import io.camunda.iam.sdk.rest.exception.RestException;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.Role;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.webapp.security.OperateURIs.IAM_CALLBACK_URI;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Profile(OperateURIs.IAM_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class IAMAuthentication extends AbstractAuthenticationToken {

  protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private IamApi iamApi;

  private DecodedJWT jwt;
  private Tokens tokens;
  private UserInfo userInfo;
  private boolean authenticated = false;

  public IAMAuthentication() {
    super(null);
  }

  @Override
  public String getCredentials() {
    return tokens.getAccessToken();
  }

  @Override
  public Object getPrincipal() {
    return jwt.getSubject();
  }

  @Override
  public void setAuthenticated(boolean authenticated) {
    if (authenticated) {
      throw new IllegalArgumentException("Create a new TokenAuthentication object to authenticate");
    }
    this.authenticated = false;
  }

  @Override
  public boolean isAuthenticated() {
    try {
      iamApi.authentication().verifyToken(tokens.getAccessToken());
    } catch (TokenVerificationException e) {
      return false;
    }
    return authenticated;
  }

  @Override
  public String getName() {
    return userInfo.getFullName();
  }

  public String getId() {
    return userInfo.getId();
  }

  public List<Role> getRoles() {
    return map(userInfo.getRoles(), Role::fromString);
  }

  public void authenticate(final HttpServletRequest req, AuthCodeDto authCodeDto) throws Exception {
    tokens = retrieveTokens(req, authCodeDto);
    userInfo = retrieveUserInfo(tokens);
    jwt = iamApi.authentication().verifyToken(tokens.getAccessToken());
    authenticated = true;
  }

  private Tokens retrieveTokens(final HttpServletRequest req, final AuthCodeDto authCodeDto) throws Exception {
    return requestWithRetry(() -> iamApi.authentication()
        .exchangeAuthCode(authCodeDto, getRedirectURI(req, IAM_CALLBACK_URI)));
  }

  private UserInfo retrieveUserInfo(final Tokens tokens) throws Exception {
    return requestWithRetry(() -> iamApi.authentication()
        .userInfo(tokens));
  }

  private <T> T requestWithRetry(final RetryOperation.RetryConsumer<T> retryConsumer) throws Exception {
    return RetryOperation.<T>newBuilder()
        .noOfRetry(10)
        .delayInterval(500, TimeUnit.MILLISECONDS)
        .retryOn(RestException.class)
        .retryConsumer(retryConsumer)
        .build()
        .retry();
  }

  public static String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80) || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    String result;
    if (contextPathIsUUID(req.getContextPath())) {
      final String clusterId = req.getContextPath().replace("/", "");
      result = redirectUri + /* req.getContextPath()+ */ redirectTo + "?uuid=" + clusterId;
    } else{
      result = redirectUri + req.getContextPath()+  redirectTo;
    }
    return result;
  }

  protected static boolean contextPathIsUUID(String contextPath) {
    try {
      UUID.fromString(contextPath.replace("/", ""));
      return true;
    }catch (Exception e){
      // Assume it isn't a UUID
      return false;
    }
  }

}
