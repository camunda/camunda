package org.camunda.optimize.plugin.security.authentication;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Resource
public interface AuthenticationExtractor {

  /**
   * Checks the request for authentication. May not return null, but always
   * an AuthenticationResult that indicates, whether
   * authentication was successful, and, if true, always provides the authenticated user.
   *
   * @param servletRequest the request to authenticate
   */
  AuthenticationResult extractAuthenticatedUser(HttpServletRequest servletRequest);
}
