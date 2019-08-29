package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import static org.springframework.http.HttpStatus.NO_CONTENT;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Controller
public class LogoutHandler extends SSOController implements LogoutSuccessHandler {

    //@RequestMapping(value=SSOWebSecurityConfig.LOGOUT_RESOURCE)
    public void onLogoutSuccess(HttpServletRequest req, HttpServletResponse res, Authentication authentication) {
        invalidateSession(req);
        String logoutUrl = String.format("https://%s/v2/logout?client_id=%s",config.getDomain(),config.getClientId());
        sendLogoutRequest(logoutUrl);
        res.setStatus(NO_CONTENT.value());
    }
    
    private void sendLogoutRequest(String logoutUrl) {
      new RestTemplate().getForObject(logoutUrl, String.class);
    }
//
//    private void redirect(HttpServletRequest req,HttpServletResponse res) {
//      String returnTo = getRedirectURI(req, "/");
//      String logoutUrl = String.format(
//              "https://%s/v2/logout?client_id=%s&returnTo=%s",
//              config.getDomain(),
//              config.getClientId(),
//              returnTo);
//      try {
//          res.sendRedirect(logoutUrl);
//      } catch(IOException e){
//        logger.error(String.format("Error in redirect to %s",logoutUrl),e);
//      }
//    }

    private void invalidateSession(HttpServletRequest request) {
        if (request.getSession() != null) {
            request.getSession().invalidate();
        }
    }

}
