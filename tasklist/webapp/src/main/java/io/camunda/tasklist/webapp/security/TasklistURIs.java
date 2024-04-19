/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.rest.ClientConfigRestService.CLIENT_CONFIG_RESOURCE;

import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

public final class TasklistURIs {

  public static final String ROOT_URL = "/";
  public static final String ROOT = ROOT_URL;
  public static final String ERROR_URL = "/error";
  public static final String GRAPHQL_URL = "/graphql";
  public static final String REST_V1_API = "/v1/";
  public static final String REST_V1_EXTERNAL_API = "/v1/external/**";
  public static final String NEW_FORM = "/new/**";
  public static final String ALL_REST_V1_API = "/v1/**";
  public static final String TASKS_URL_V1 = "/v1/tasks";
  public static final String VARIABLES_URL_V1 = "/v1/variables";
  public static final String FORMS_URL_V1 = "/v1/forms";
  public static final String FILTERS_URL_V1 = "/v1/tasks/filters";
  public static final String USERS_URL_V1 = "/v1/internal/users";
  public static final String DEV_UTIL_URL_V1 = "/v1/external/devUtil";
  public static final String PROCESSES_URL_V1 = "/v1/internal/processes";
  public static final String EXTERNAL_PROCESS_URL_V1 = "/v1/external/process";

  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String SSO_CALLBACK = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";
  public static final String IDENTITY_CALLBACK_URI = "/identity-callback";
  public static final String REQUESTED_URL = "requestedUrl";
  public static final String COOKIE_JSESSIONID = "TASKLIST-SESSION";
  public static final String START_PUBLIC_PROCESS = "/new/";
  public static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";

  private TasklistURIs() {}

  public static final RequestMatcher[] getAuthWhitelist(
      final HandlerMappingIntrospector introspector) {
    final RequestMatcher[] requestMatchers = {
      AntPathRequestMatcher.antMatcher("/webjars/**"),
      AntPathRequestMatcher.antMatcher(CLIENT_CONFIG_RESOURCE),
      new MvcRequestMatcher(introspector, ERROR_URL),
      AntPathRequestMatcher.antMatcher(NO_PERMISSION),
      AntPathRequestMatcher.antMatcher(LOGIN_RESOURCE),
      AntPathRequestMatcher.antMatcher(LOGOUT_RESOURCE),
      AntPathRequestMatcher.antMatcher(REST_V1_EXTERNAL_API),
      new MvcRequestMatcher(introspector, NEW_FORM)
    };
    return requestMatchers;
  }

  // Used as constants class

}
