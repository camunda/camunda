/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile("pt-security")
public class PhysicalTenantWhoamiController {

  public record Whoami(String tenantId, String principal) {}

  @GetMapping("/physical-tenant/{tenantId}/whoami")
  @ResponseBody
  public Whoami whoami(@PathVariable final String tenantId, final Authentication authentication) {
    return new Whoami(tenantId, authentication != null ? authentication.getName() : "anonymous");
  }

  @GetMapping("/v2/physical-tenants/{tenantId}/whoami")
  @ResponseBody
  public Whoami whoamiApi(
      @PathVariable final String tenantId, final Authentication authentication) {
    return new Whoami(tenantId, authentication != null ? authentication.getName() : "anonymous");
  }

  // Minimal SPA-style page that exercises the realistic webapp → API call.
  // Served from the OAuth2-protected webapp chain; the embedded JavaScript fetches the
  // bearer-only API endpoint from the same origin. Surfaces the session-vs-bearer gap
  // (spec OQ-1): the cookie's Path is /physical-tenant/<t> so the browser won't send it
  // to /v2/physical-tenants/<t>/*; and even if it did, the API chain is sessionless and
  // wouldn't honour it.
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
            <p>Session principal (server-rendered): <b>%s</b></p>

            <h2>Webapp /whoami (cookie auth, same chain)</h2>
            <button onclick="callWebappWhoami()">GET /physical-tenant/%s/whoami</button>
            <pre id="webapp-result">(click)</pre>

            <h2>API /v2/.../whoami (bearer chain) — SPA call from this tab</h2>
            <p>Realistic SPA flow: this tab is logged in via the webapp chain's session cookie.
            The fetch below has no Authorization header. Expected: the cookie is NOT sent
            (Path=/physical-tenant/%s does not cover /v2/physical-tenants/%s), and even if it
            were, the API chain only accepts bearer tokens.</p>
            <button onclick="callApiWhoami()">GET /v2/physical-tenants/%s/whoami (no token)</button>
            <pre id="api-result">(click)</pre>

            <h2>Diagnostics</h2>
            <button onclick="showCookies()">Show document.cookie (HttpOnly cookies are invisible here)</button>
            <pre id="cookies">(click)</pre>

            <script>
              async function callWebappWhoami() {
                const r = await fetch('/physical-tenant/%s/whoami', { credentials: 'include' });
                const text = await r.text();
                document.getElementById('webapp-result').textContent = r.status + ' ' + r.statusText + '\\n' + text;
              }
              async function callApiWhoami() {
                try {
                  const r = await fetch('/v2/physical-tenants/%s/whoami', { credentials: 'include' });
                  const text = await r.text();
                  document.getElementById('api-result').textContent = r.status + ' ' + r.statusText + '\\n' + text;
                } catch (e) {
                  document.getElementById('api-result').textContent = 'fetch error: ' + e;
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
            tenantId, tenantId, principal, tenantId, tenantId, tenantId, tenantId, tenantId,
            tenantId);
  }
}
