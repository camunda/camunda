/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * SPA-style HTML demo page for the per-tenant webapp chain. Served from the OAuth2-protected webapp
 * chain; the embedded JavaScript fetches the API endpoint from the same origin without an
 * Authorization header. Available under two URLs:
 *
 * <ul>
 *   <li>{@code /physical-tenant/{tenantId}/app} — prefixed access path. Session cookie at {@code
 *       Path=/physical-tenant/<t>} covers {@code /physical-tenant/<t>/v2/*} and the API chain reads
 *       it via the shared {@code SessionRepositoryFilter}.
 *   <li>{@code /app} — unprefixed default access path. Mirrors how existing Camunda webapps
 *       (operate, tasklist) are reached.
 * </ul>
 *
 * <p>The JavaScript lives at the sibling {@code /app/script.js} (or {@code
 * /physical-tenant/{tenantId}/app/script.js}) endpoint and is loaded via {@code <script src="...">}
 * — inline scripts and inline event handlers would be blocked by CSL's Content Security Policy
 * ({@code script-src 'self'}, {@code script-src-attr 'none'}).
 *
 * <p>This is a plain {@code @Controller} (not {@code @CamundaRestController}) because the path
 * {@code /app} is not a {@code /v2/...} REST endpoint — the {@code
 * PhysicalTenantRequestMappingHandlerMapping} correctly ignores it.
 */
@Controller
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantAppController {

  @GetMapping(
      value = {"/physical-tenant/{tenantId}/app", "/app"},
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String app(
      @PathVariable(name = "tenantId", required = false) final @Nullable String tenantId,
      final Authentication authentication) {
    final boolean prefixed = tenantId != null;
    final String resolvedTenant = prefixed ? tenantId : "default";
    final String principal = authentication != null ? authentication.getName() : "anonymous";
    final String accessPathLabel = prefixed ? "/physical-tenant/" + resolvedTenant : "/ (default)";
    final String cookieScopeLabel = prefixed ? "/physical-tenant/" + resolvedTenant : "/";
    final String basePrefix = prefixed ? "/physical-tenant/" + resolvedTenant : "";
    final String scriptUrl = basePrefix + "/app/script.js";
    final String sameAccessPathApiUrl = basePrefix + "/v2/whoami";
    final String apiClientUrl = "/v2/physical-tenants/" + resolvedTenant + "/whoami";

    return ("""
            <!doctype html>
            <html><head><meta charset="utf-8"><title>PT %s</title>
            <style>body{font-family:sans-serif;margin:2em;max-width:60em}
            pre{background:#f4f4f4;padding:1em;white-space:pre-wrap}
            button{padding:.5em 1em;margin-right:.5em}
            h2{margin-top:2em}
            code{background:#eef;padding:0 .25em}</style></head><body>
            <h1>Physical tenant: %s <small>(access path: <code>%s</code>)</small></h1>
            <p>Session principal (server-rendered): <b>%s</b>. This page is served from the
            OAuth2-protected webapp chain — landing here means the session cookie is in place at
            <code>Path=%s</code>, which is the precondition the SPA-style fetches below rely on.</p>

            <h2>1. Same-access-path API call (cookie shared)</h2>
            <p>SPA call to the API URL that sits inside this page's cookie scope. The browser sends
            the session cookie automatically, the API chain reads it via the shared
            <code>SessionRepositoryFilter</code>, and authentication resolves to the same principal
            as the webapp chain. Expected: <b>200</b>, no Authorization header needed.</p>
            <button id="same-button" data-url="%s">GET <code>%s</code></button>
            <pre id="same-result">(click)</pre>

            <h2>2. Direct API-client URL (bearer-only territory)</h2>
            <p>SPA call to the REST-conventional PT API scheme — meant for API clients that bring
            their own Bearer token. From this page, no Bearer header is sent, and either (a) the
            cookie Path doesn't cover the URL (prefixed access path) or (b) the cookie name doesn't
            match what the prefixed default API chain looks for (unprefixed access path) — both
            paths land at <b>401</b>. This isolates the two URL schemes by purpose: the SPA uses
            its same-access-path URL; external clients use this one with their bearer.</p>
            <button id="api-client-button" data-url="%s">GET <code>%s</code></button>
            <pre id="api-client-result">(click)</pre>

            <h2>Diagnostics</h2>
            <p>The SPA flow works because (a) the same-access-path API URL is under the cookie's
            <code>Path</code>, so the browser sends the session cookie automatically, and (b) the
            API chain installs the same per-tenant <code>SessionRepositoryFilter</code> as the
            webapp chain and reuses the <code>SecurityContext</code> stored at OAuth2 login.</p>
            <button id="cookies-button">Show document.cookie (HttpOnly cookies are invisible here)</button>
            <pre id="cookies">(click)</pre>

            <script src="%s"></script>
            </body></html>
            """)
        .formatted(
            resolvedTenant, // <title>
            resolvedTenant, // <h1> tenant name
            accessPathLabel, // <h1> small access-path label
            principal, // server-rendered principal
            cookieScopeLabel, // cookie Path label in the intro
            sameAccessPathApiUrl, // button 1 data-url
            sameAccessPathApiUrl, // button 1 label
            apiClientUrl, // button 2 data-url
            apiClientUrl, // button 2 label
            scriptUrl); // <script src>
  }

  /**
   * External JavaScript for the SPA demo page. Lives at a sibling URL so it satisfies CSL's CSP
   * ({@code script-src 'self'}) — inline {@code <script>} blocks and {@code onclick=} attribute
   * handlers are blocked by {@code script-src-attr 'none'} on CSL's chains.
   *
   * <p>The script wires event listeners for the three buttons declared by {@link #app}. Each button
   * uses a {@code data-url} attribute to declare the URL it should fetch, so the script is generic
   * across the prefixed and unprefixed access paths.
   */
  @GetMapping(
      value = {"/physical-tenant/{tenantId}/app/script.js", "/app/script.js"},
      produces = "application/javascript")
  @ResponseBody
  public String script() {
    return """
           async function callFetch(buttonId, resultId) {
             const button = document.getElementById(buttonId);
             const result = document.getElementById(resultId);
             const url = button.dataset.url;
             try {
               const r = await fetch(url, { credentials: 'include' });
               const text = await r.text();
               result.textContent = r.status + ' ' + r.statusText + '\\n' + text;
             } catch (e) {
               result.textContent = 'fetch error: ' + e;
             }
           }
           document.getElementById('same-button')
             .addEventListener('click', () => callFetch('same-button', 'same-result'));
           document.getElementById('api-client-button')
             .addEventListener('click', () => callFetch('api-client-button', 'api-client-result'));
           document.getElementById('cookies-button')
             .addEventListener('click', () => {
               const el = document.getElementById('cookies');
               el.textContent =
                 document.cookie || '(no visible cookies — session cookies are HttpOnly)';
             });
           """;
  }
}
