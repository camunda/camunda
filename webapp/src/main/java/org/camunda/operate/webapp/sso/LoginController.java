package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Controller
public class LoginController extends SSOController {

    @RequestMapping(value = SSOWebSecurityConfig.LOGIN_RESOURCE, method = {RequestMethod.GET,RequestMethod.POST})
    protected String login(final HttpServletRequest req) {
        logger.debug("Performing login");
        String authorizeUrl = authenticator.buildAuthorizeUrl(req, getRedirectURI(req,SSOWebSecurityConfig.CALLBACK_URI ))
                .withAudience(String.format("https://%s/userinfo", config.getDomain()))
                .withScope("openid profile email")
                .build();
        return redirect(authorizeUrl);
    }

}
