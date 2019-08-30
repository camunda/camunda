package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

import com.auth0.AuthenticationController;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public abstract class SSOController {

  @Autowired
  protected AuthenticationController authenticator;
  
  @Autowired
  protected SSOWebSecurityConfig config;

  protected  final Logger logger = LoggerFactory.getLogger(this.getClass());
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
}