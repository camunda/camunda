package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Controller
public class ErrorController extends SSOController implements org.springframework.boot.web.servlet.error.ErrorController{

    private static final String ERROR_PATH = "/error";

    @RequestMapping(ERROR_PATH)
    protected String error(final RedirectAttributes redirectAttributes) throws IOException {
        return redirect(SSOWebSecurityConfig.LOGIN_RESOURCE);
    }

    @Override
    public String getErrorPath() {
      return ERROR_PATH;
    }

}