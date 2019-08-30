package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import java.io.IOException;
import org.springframework.boot.web.servlet.error.ErrorController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.auth0.jwt.JWT;

@Controller
@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public class SSOController implements ErrorController{

  
  private static final String ERROR_PATH = "/error";

  @Autowired
  protected AuthenticationController authenticator;
  
  @Autowired
  protected SSOWebSecurityConfig config;

  protected  final Logger logger = LoggerFactory.getLogger(this.getClass());
  
  @RequestMapping(ERROR_PATH)
  protected String error(final RedirectAttributes redirectAttributes) throws IOException {
      return redirect(SSOWebSecurityConfig.LOGIN_RESOURCE);
  }

  @RequestMapping(value = SSOWebSecurityConfig.LOGIN_RESOURCE, method = {RequestMethod.GET,RequestMethod.POST})
  protected String login(final HttpServletRequest req) {
      logger.debug("Performing login");
      String authorizeUrl = authenticator.buildAuthorizeUrl(req, getRedirectURI(req,SSOWebSecurityConfig.CALLBACK_URI ))
              .withAudience(String.format("https://%s/userinfo", config.getDomain()))
              .withScope("openid profile email")
              .build();
      return redirect(authorizeUrl);
  }
  
  @RequestMapping(value = SSOWebSecurityConfig.CALLBACK_URI, method = RequestMethod.GET)
  protected void getCallback(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
      handle(req, res);
  }

  @RequestMapping(value =  SSOWebSecurityConfig.CALLBACK_URI, method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  protected void postCallback(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
      handle(req, res);
  }

  protected void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
      try {
          Tokens tokens = authenticator.handle(req);
          TokenAuthentication tokenAuth = new TokenAuthentication(JWT.decode(tokens.getIdToken()),config);
          SecurityContextHolder.getContext().setAuthentication(tokenAuth);
          res.sendRedirect("/#/");
      } catch (AuthenticationException | IdentityVerificationException e) {
          logger.error("Error in authentication callback", e);
          SecurityContextHolder.clearContext();
          res.sendRedirect(SSOWebSecurityConfig.LOGIN_RESOURCE);
      }
  }
  
  protected String getRedirectURI(final HttpServletRequest req,final String redirectTo) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80) || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    return redirectUri + redirectTo;
  }
  
  protected String redirect(String toURL) {
    return "redirect:"+toURL;
  }
 
  @Override
  public String getErrorPath() {
    return ERROR_PATH;
  }

}