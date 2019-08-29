package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.auth0.jwt.JWT;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Controller
public class CallbackController extends SSOController {

    @Autowired
    SSOWebSecurityConfig configuration;
  
    @RequestMapping(value = SSOWebSecurityConfig.CALLBACK_URI, method = RequestMethod.GET)
    protected void getCallback(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
        handle(req, res);
    }

    @RequestMapping(value =  SSOWebSecurityConfig.CALLBACK_URI, method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    protected void postCallback(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
        handle(req, res);
    }

    private void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            Tokens tokens = authenticator.handle(req);
            TokenAuthentication tokenAuth = new TokenAuthentication(JWT.decode(tokens.getIdToken()),configuration);
            SecurityContextHolder.getContext().setAuthentication(tokenAuth);
            res.sendRedirect("/#/");
        } catch (AuthenticationException | IdentityVerificationException e) {
            logger.error("Error in authentication callback", e);
            SecurityContextHolder.clearContext();
            res.sendRedirect(SSOWebSecurityConfig.LOGIN_RESOURCE);
        }
    }

}

