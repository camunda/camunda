/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * SPA-style HTML demo page for the per-tenant webapp chain. Served from the OAuth2-protected webapp
 * chain; the embedded JavaScript fetches the API endpoint from the same origin without an
 * Authorization header. The session cookie at {@code Path=/physical-tenant/<t>} is automatically
 * sent on {@code /physical-tenant/<t>/v2/*} and the API chain reads it via the shared {@code
 * SessionRepositoryFilter}.
 *
 * <p>This is a plain {@code @Controller} (not {@code @CamundaRestController}) because the path
 * {@code /physical-tenant/{tenantId}/app} is not a {@code /v2/...} REST endpoint — the {@code
 * PhysicalTenantRequestMappingHandlerMapping} correctly ignores it.
 */
@Controller
@Profile("pt-security")
public class PhysicalTenantAppController {

  // Minimal SPA-style page that exercises the realistic webapp → API call.
  // Served from the OAuth2-protected webapp chain; the embedded JavaScript fetches the
  // API endpoint from the same origin without an Authorization header. With OQ-1 resolved
  // (API moved under the tenant prefix, API chain session-aware) this fetch now succeeds:
  // the session cookie at Path=/physical-tenant/<t> is automatically sent on
  // /physical-tenant/<t>/v2/* and the API chain reads it via the shared
  // SessionRepositoryFilter.
  @GetMapping(value = "/physical-tenant/{tenantId}/app", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String app(@PathVariable final String tenantId, final Authentication authentication) {
    final String principal = authentication != null ? authentication.getName() : "anonymous";
    return ("""
            <!doctype html>
            <html><head><meta charset="utf-8"><title>PT %s</title>
            <style>body{font-family:sans-serif;margin:2em;max-width:60em}
            pre{background:#f4f4f4;padding:1em;white-space:pre-wrap}
            button{padding:.5em 1em;margin-right:.5em}
            h2{margin-top:2em}</style></head><body>
            <h1>Physical tenant: %s</h1>
            <p>Session principal (server-rendered): <b>%s</b>. This page is served from the
            OAuth2-protected webapp chain — landing here means the session cookie is in place,
            which is the precondition the SPA-style fetches below rely on.</p>

            <h2>API /physical-tenant/&lt;id&gt;/v2/whoami (webapp-aligned URL, session-shared)</h2>
            <p>The session cookie at Path=/physical-tenant/%s covers this URL too,
            and the API chain accepts session auth (in addition to bearer tokens for
            non-browser clients). Expected: <b>200</b> with the same principal as the webapp
            chain — no Authorization header needed.</p>
            <button onclick="callApiWhoami()">GET /physical-tenant/%s/v2/whoami (no Authorization header)</button>
            <pre id="api-result">(click)</pre>

            <h2>API /v2/physical-tenants/&lt;id&gt;/whoami (direct API-client URL, outside cookie scope)</h2>
            <p>This URL is the existing REST-conventional PT API scheme — addressed for API
            clients that bring their own Bearer token. It sits <b>outside</b> the webapp cookie's
            <code>Path=/physical-tenant/%s</code> scope, so the browser does NOT send the session
            cookie on this fetch. With no Authorization header either, the API chain returns
            <b>401</b>. This is the correct outcome for the SPA flow against the API-client URL —
            it isolates the two URL schemes by purpose.</p>
            <button onclick="callApiClientWhoami()">GET /v2/physical-tenants/%s/whoami (no Authorization header)</button>
            <pre id="api-client-result">(click)</pre>

            <h2>Diagnostics</h2>
            <p>The webapp-aligned SPA flow works because (a) the API URL is under the cookie's
            Path scope, so the browser sends the session cookie automatically, and (b) the API
            chain installs the same per-tenant SessionRepositoryFilter as the webapp chain
            and reuses the SecurityContext stored at OAuth2 login.</p>
            <button onclick="showCookies()">Show document.cookie (HttpOnly cookies are invisible here)</button>
            <pre id="cookies">(click)</pre>

            <script>
              async function callApiWhoami() {
                try {
                  const r = await fetch('/physical-tenant/%s/v2/whoami', { credentials: 'include' });
                  const text = await r.text();
                  document.getElementById('api-result').textContent = r.status + ' ' + r.statusText + '\\n' + text;
                } catch (e) {
                  document.getElementById('api-result').textContent = 'fetch error: ' + e;
                }
              }
              async function callApiClientWhoami() {
                try {
                  const r = await fetch('/v2/physical-tenants/%s/whoami', { credentials: 'include' });
                  const text = await r.text();
                  document.getElementById('api-client-result').textContent = r.status + ' ' + r.statusText + '\\n' + text;
                } catch (e) {
                  document.getElementById('api-client-result').textContent = 'fetch error: ' + e;
                }
              }
              function showCookies() {
                document.getElementById('cookies').textContent =
                  document.cookie || '(no visible cookies — session cookies are HttpOnly)';
              }
            </script>
            </body></html>
            """)
        .formatted(
            tenantId, // <title>
            tenantId, // <h1>
            principal, // server-rendered principal
            tenantId, // Path=/physical-tenant/{}
            tenantId, // api button URL label
            tenantId, // Path=/physical-tenant/{}
            tenantId, // api-client button URL label
            tenantId, // callApiWhoami fetch URL
            tenantId); // callApiClientWhoami fetch URL
  }
}
