#!/usr/bin/env node
// PoC of a c8ctl-style CLI that logs in with nothing but the cluster URL:
//   1. GET <cluster>/.well-known/camunda-auth          -> auth method, issuer, public client
//   2. GET <issuer>/.well-known/openid-configuration   -> authorize / token endpoints
//   3. authorization code + PKCE in the browser, redirected to a random 127.0.0.1 port (RFC 8252)
//   4. later commands call the cluster API with the stored token, refreshing it when it expires
//
// Configuration works like a kubeconfig: clusters, users (an identity with its tokens) and
// contexts (cluster + physical tenant + user), one of them current. A token is only valid for the
// physical tenant it was issued for, so every login creates a user and a context of its own: you
// can be logged in to pt1 via one identity provider and to pt2 via another, and switch.
//
// Zero dependencies; needs Node 18+. The configuration lives in ~/.c8poc/config.json.

import { execFile } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { homedir } from "node:os";
import { join } from "node:path";
import { createInterface } from "node:readline/promises";

const CONFIG_DIR = process.env.C8POC_HOME ?? join(homedir(), ".c8poc");
const CONFIG_FILE = join(CONFIG_DIR, "config.json");
const LEGACY_SESSION_FILE = join(CONFIG_DIR, "session.json");
const DEFAULT_CLUSTER = "http://localhost:8080";
const REFRESH_LEEWAY_SECONDS = 30;

const USAGE = `Usage: c8poc [--context <name>] <command> [options]

Log in:
  login [url]                    Log in, store a user and a context, make the context current.
                                 Without url: log in to the current context (or --context);
                                 url: a cluster or a physical tenant, e.g.
                                 ${DEFAULT_CLUSTER}/physical-tenants/pt1
      --physical-tenant <id>     Physical tenant (instead of putting it in the url); without
                                 either, asks if the cluster has more than the default one
      --provider <id>            Identity provider (instead of choosing in the browser)
      --name <context>           Context name (default: <host>[/<physical tenant>]);
                                 c8poc --context <name> login ... works as well
      --no-browser               Only print the login URL (open it on this machine)
  logout                         Delete the context and its user
  physical-tenants [url]         List the cluster's physical tenants

Use the cluster (with the current context, or --context <name>):
  whoami                         Cluster, physical tenant and identity of the context
  topology                       GET /v2/topology
  api <path>                     GET any path below the physical tenant's API

Configuration (like kubectl config):
  config discover [url]          Add a context for each physical tenant of the cluster (not
                                 logged in yet; log in with c8poc --context <name> login)
  config set-context <name> [--cluster <url>] [--physical-tenant <id>]
                                 Create or change a context
  config get-contexts            List contexts (* = current)
  config current-context         Print the current context
  config use-context <name>      Switch the current context
  config rename-context <old> <new>
  config delete-context <name>   Delete a context (and its user, if no other context uses it)
  config get-clusters            List clusters
  config get-users               List users
  config view                    Print the configuration, tokens redacted
`;

// ---------- helpers ----------

const info = (...m) => console.error(...m);

function takeFlag(args, name, hasValue) {
  const i = args.indexOf(name);
  if (i < 0) return undefined;
  const [, value] = args.splice(i, hasValue ? 2 : 1);
  return hasValue ? value : true;
}

async function getJson(url, init) {
  const res = await fetch(url, init);
  const body = await res.text();
  if (!res.ok) throw new Error(`${init?.method ?? "GET"} ${url} -> ${res.status}: ${body}`);
  return JSON.parse(body);
}

const base64url = (buf) => buf.toString("base64url");

function decodeJwt(token) {
  try {
    return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString());
  } catch {
    return {};
  }
}

function openBrowser(url) {
  const cmd = { darwin: "open", win32: "explorer" }[process.platform] ?? "xdg-open";
  execFile(cmd, [url], () => {});
}

async function prompt(question) {
  const rl = createInterface({ input: process.stdin, output: process.stderr });
  try {
    return await rl.question(question);
  } finally {
    rl.close();
  }
}

// ---------- configuration (kubeconfig style) ----------

const emptyConfig = () => ({ currentContext: undefined, clusters: {}, users: {}, contexts: {} });

function loadConfig() {
  if (existsSync(CONFIG_FILE)) return JSON.parse(readFileSync(CONFIG_FILE, "utf8"));
  return existsSync(LEGACY_SESSION_FILE) ? migrateLegacySession() : emptyConfig();
}

function saveConfig(config) {
  mkdirSync(CONFIG_DIR, { recursive: true, mode: 0o700 });
  writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2), { mode: 0o600 });
}

// session.json of earlier c8poc versions: one session, or contexts holding everything
function migrateLegacySession() {
  const legacy = JSON.parse(readFileSync(LEGACY_SESSION_FILE, "utf8"));
  const contexts = legacy.contexts ?? { [contextName(legacy.clusterUrl)]: legacy };
  const config = emptyConfig();
  for (const [name, { clusterUrl, physicalTenant, tenant, apiBase, ...user }] of Object.entries(contexts)) {
    addLogin(config, name, { clusterUrl, physicalTenant: physicalTenant ?? tenant ?? "default", apiBase: apiBase ?? "" }, user);
  }
  config.currentContext = legacy.current ?? Object.keys(contexts)[0];
  saveConfig(config);
  rmSync(LEGACY_SESSION_FILE, { force: true });
  return config;
}

// Stores what a login produced: the cluster (shared by its contexts), a user and a context.
function addLogin(config, name, target, user) {
  const userName = `${identity(user) ?? user.method}@${name}`;
  config.users[userName] = user;
  setContext(config, name, target, userName);
}

// A context without a user is configured but not logged in yet.
function setContext(config, name, target, userName) {
  const clusterName = new URL(target.clusterUrl).host;
  config.clusters[clusterName] = { server: target.clusterUrl };
  const previous = config.contexts[name];
  config.contexts[name] = { cluster: clusterName, physicalTenant: target.physicalTenant, apiBase: target.apiBase, user: userName };
  if (previous?.user && previous.user !== userName) deleteUserIfUnused(config, previous.user);
  if (previous && previous.cluster !== clusterName) deleteClusterIfUnused(config, previous.cluster);
}

function deleteUserIfUnused(config, userName) {
  if (!Object.values(config.contexts).some((c) => c.user === userName)) delete config.users[userName];
}

function deleteClusterIfUnused(config, clusterName) {
  if (!Object.values(config.contexts).some((c) => c.cluster === clusterName)) delete config.clusters[clusterName];
}

// A context with its cluster and user resolved - what the commands work with.
function resolveContext(config, name) {
  const resolved = name ?? config.currentContext;
  if (!resolved) throw new Error("no current context - run: c8poc login [url], or c8poc config use-context <name>");
  const context = config.contexts[resolved];
  if (!context) throw new Error(`no context '${resolved}' - see: c8poc config get-contexts`);
  const cluster = config.clusters[context.cluster];
  if (!cluster) throw new Error(`context '${resolved}' has no cluster - see: c8poc config set-context`);
  const user = config.users[context.user];
  if (!user) throw new Error(`context '${resolved}' is not logged in - run: c8poc --context ${resolved} login`);
  return { name: resolved, context, clusterUrl: cluster.server, user };
}

function identity(user) {
  if (user.method === "basic") return user.username;
  if (user.method === "oidc") {
    const claims = decodeJwt(user.accessToken);
    return claims.preferred_username ?? claims.sub;
  }
  return undefined;
}

function contextName(clusterUrl, physicalTenant) {
  return new URL(clusterUrl).host + (physicalTenant && physicalTenant !== "default" ? `/${physicalTenant}` : "");
}

// "https://oc/physical-tenants/pt1" -> cluster "https://oc", physical tenant "pt1"
function parseTarget(url, physicalTenantFlag) {
  const trimmed = url.replace(/\/$/, "");
  const match = trimmed.match(/^(.*)\/physical-tenants\/([^/]+)$/);
  if (match && physicalTenantFlag && physicalTenantFlag !== match[2]) {
    throw new Error(`--physical-tenant ${physicalTenantFlag} contradicts the url's physical tenant ${match[2]}`);
  }
  return match ? { clusterUrl: match[1], physicalTenant: match[2] } : { clusterUrl: trimmed, physicalTenant: physicalTenantFlag };
}

function withTokens(user, tokens) {
  return {
    ...user,
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token ?? user.refreshToken,
    expiresAt: Math.floor(Date.now() / 1000) + (tokens.expires_in ?? 300),
  };
}

// ---------- OIDC: browser login ----------

const DEFAULT_REDIRECT_URI = "http://127.0.0.1/callback";
const LOOPBACK_ADDRESSES = { "127.0.0.1": ["127.0.0.1"], "[::1]": ["::1"], localhost: ["127.0.0.1", "::1"] };
const BIND_ATTEMPTS = 5;

// The redirect URI must match the IdP registration exactly, except for the loopback port
// (RFC 8252), so host and path come from discovery and only the port is ours. "localhost" is a
// name the browser may resolve to 127.0.0.1 or ::1, so it is served on both, on the same port.
function parseRedirectUri(redirectUri) {
  const uri = new URL(redirectUri ?? DEFAULT_REDIRECT_URI);
  const addresses = LOOPBACK_ADDRESSES[uri.hostname];
  if (uri.protocol !== "http:" || !addresses) {
    throw new Error(`redirect URI ${uri} is not an http loopback URI; a CLI cannot receive the browser redirect there`);
  }
  // URL normalizes a missing path to "/", but the IdP compares the string: "http://localhost"
  // and "http://localhost/" are different registrations (Entra ID registers the former).
  const rawPath = (redirectUri ?? DEFAULT_REDIRECT_URI).match(/^http:\/\/(?:\[[^\]]*\]|[^/:?#]+)(?::\d+)?(\/[^?#]*)?/)?.[1] ?? "";
  return { host: uri.hostname, addresses, fixedPort: uri.port ? Number(uri.port) : undefined, path: rawPath };
}

function listen(server, port, address) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, address, () => {
      server.off("error", reject);
      resolve(server.address().port);
    });
  });
}

// Serves `handler` on every given loopback address, all on the same port: the first address picks
// it (unless fixed), the others must get the same one - if one is taken there, start over.
async function listenOnLoopback(handler, addresses, fixedPort) {
  for (let attempt = 1; ; attempt++) {
    const servers = [];
    try {
      let port = fixedPort ?? 0;
      for (const address of addresses) {
        const server = createServer(handler);
        servers.push(server);
        port = await listen(server, port, address);
      }
      // A browser keeps connections to the page open (and preconnects ones it never uses), which
      // would keep the CLI running until the tab is closed: drop them once the login is decided.
      return {
        port,
        close: () =>
          servers.forEach((s) => {
            s.close();
            s.closeAllConnections();
          }),
      };
    } catch (e) {
      servers.forEach((s) => s.close());
      if (e.code !== "EADDRINUSE" || fixedPort || attempt >= BIND_ATTEMPTS) {
        throw new Error(`cannot listen for the login redirect on ${addresses.join(", ")}: ${e.message}`);
      }
    }
  }
}

// Logs in through the browser. A small server on the loopback interface lets the user pick the
// identity provider (when the physical tenant offers more than one), starts authorization code +
// PKCE for it, receives the redirect, redeems the code and shows the outcome - all in the browser.
async function browserLogin(providers, { providerId, noBrowser, cluster, physicalTenant, contextName }) {
  const offered = providerId ? providers.filter((p) => p.id === providerId) : providers;
  if (!offered.length) throw new Error(`no provider '${providerId}', available: ${providers.map((p) => p.id).join(", ")}`);
  const redirects = new Map(offered.map((p) => [p.id, parseRedirectUri(p.redirect_uri)]));
  const fixedPorts = [...new Set([...redirects.values()].map((r) => r.fixedPort).filter(Boolean))];
  if (fixedPorts.length > 1) throw new Error(`the providers need different fixed ports (${fixedPorts.join(", ")}); pick one with --provider`);
  const addresses = [...new Set([...redirects.values()].flatMap((r) => r.addresses))];

  let resolveLogin, rejectLogin;
  const result = new Promise((res, rej) => ((resolveLogin = res), (rejectLogin = rej)));
  const pending = new Map(); // state -> one started login attempt
  let port;
  const context = { cluster, physicalTenant, contextName };

  const handler = async (req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    const query = url.searchParams;
    const send = (status, html, after) => {
      res.writeHead(status, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      res.end(html, after);
    };
    try {
      // the identity provider's redirect (Entra ID registers no path, so it may arrive on "/")
      if (query.has("state") && (query.has("code") || query.has("error"))) {
        const attempt = pending.get(query.get("state"));
        if (!attempt || url.pathname !== (attempt.redirect.path || "/")) {
          return send(400, resultPage({ ok: false, title: "This login link has expired", detail: "Start the login again from the terminal." }));
        }
        if (query.has("error")) {
          const detail = `${query.get("error")}${query.get("error_description") ? `: ${query.get("error_description")}` : ""}`;
          return send(400, resultPage({ ok: false, title: "Login failed", detail, provider: attempt.provider, context }), () =>
            rejectLogin(new Error(`login failed at ${attempt.provider.issuer}: ${detail}`)));
        }
        const tokens = await redeemCode(attempt, query.get("code"));
        try {
          checkAccessToken(tokens.access_token, attempt.provider);
        } catch (e) {
          return send(400, resultPage({ ok: false, title: "The cluster would reject this login", detail: e.message, provider: attempt.provider, context }), () => rejectLogin(e));
        }
        const claims = decodeJwt(tokens.access_token);
        return send(200, resultPage({ ok: true, identity: claims.preferred_username ?? claims.sub, provider: attempt.provider, context }), () =>
          resolveLogin({ provider: attempt.provider, meta: attempt.meta, tokens }));
      }
      if (url.pathname === "/start") {
        const provider = offered.find((p) => p.id === query.get("provider"));
        if (!provider) return send(404, resultPage({ ok: false, title: "Unknown identity provider", detail: query.get("provider") ?? "" }));
        const attempt = await startAttempt(provider, redirects.get(provider.id), port);
        pending.set(attempt.state, attempt);
        res.writeHead(302, { location: attempt.authorizeUrl, "cache-control": "no-store" });
        return res.end();
      }
      if (url.pathname === "/cancel") {
        return send(200, resultPage({ ok: false, title: "Login cancelled", detail: "Nothing was changed.", context }), () => rejectLogin(new Error("login cancelled in the browser")));
      }
      if (url.pathname === "/" && !query.size) return send(200, chooserPage(offered, context));
      res.writeHead(404).end();
    } catch (e) {
      send(500, resultPage({ ok: false, title: "Login failed", detail: e.message, context }), () => rejectLogin(e));
    }
  };

  const server = await listenOnLoopback(handler, addresses, fixedPorts[0]);
  port = server.port;
  const pageHost = addresses.includes("127.0.0.1") ? "127.0.0.1" : "[::1]";
  const entry = offered.length === 1 ? `/start?provider=${encodeURIComponent(offered[0].id)}` : "/";
  const loginUrl = `http://${pageHost}:${port}${entry}`;
  if (noBrowser) {
    info(`Open this URL to log in:\n\n  ${loginUrl}\n`);
  } else {
    info(`Opening the browser to log in${offered.length > 1 ? " - choose your identity provider there" : ""}...`);
    info(`(if it does not open: ${loginUrl})`);
    openBrowser(loginUrl);
  }
  try {
    return await result;
  } finally {
    server.close();
  }
}

async function startAttempt(provider, redirect, port) {
  const meta = await getJson(`${provider.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`);
  const verifier = base64url(randomBytes(32));
  const state = base64url(randomBytes(16));
  const redirectUri = `http://${redirect.host}:${port}${redirect.path}`;
  const authorize = new URL(meta.authorization_endpoint);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: provider.client_id,
    redirect_uri: redirectUri,
    scope: (provider.scopes?.length ? provider.scopes : ["openid"]).join(" "),
    state,
    code_challenge: base64url(createHash("sha256").update(verifier).digest()),
    code_challenge_method: "S256",
    ...(provider.resource ? { resource: provider.resource } : {}),
  });
  return { provider, meta, redirect, redirectUri, verifier, state, authorizeUrl: authorize.toString() };
}

function redeemCode(attempt, code) {
  return getJson(attempt.meta.token_endpoint, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: attempt.redirectUri,
      client_id: attempt.provider.client_id,
      code_verifier: attempt.verifier,
      ...(attempt.provider.resource ? { resource: attempt.provider.resource } : {}),
    }),
  });
}

// ---------- OIDC: pages (Camunda / Carbon look, self-contained) ----------

const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const issuerLabel = (issuer) => issuer.replace(/^https?:\/\//, "").replace(/\/$/, "");

function contextLine({ cluster, physicalTenant }) {
  if (!cluster) return "";
  const host = new URL(cluster).host;
  return `<p class="meta">Cluster <strong>${esc(host)}</strong>${physicalTenant && physicalTenant !== "default" ? ` &middot; Physical tenant <strong>${esc(physicalTenant)}</strong>` : ""}</p>`;
}

function chooserPage(providers, context) {
  const tiles = providers
    .map((p) => {
      const name = p.name ?? p.id;
      return `<li><a class="tile" href="/start?provider=${encodeURIComponent(p.id)}">
        <span class="badge" aria-hidden="true">${esc(name.trim()[0]?.toUpperCase() ?? "?")}</span>
        <span class="tile-text"><span class="tile-title">${esc(name)}</span><span class="tile-sub">${esc(issuerLabel(p.issuer))}</span></span>
        <svg class="chevron" width="20" height="20" viewBox="0 0 32 32" aria-hidden="true"><path d="M18 6l-1.43 1.393L24.15 15H4v2h20.15l-7.58 7.573L18 26l10-10z"/></svg>
      </a></li>`;
    })
    .join("");
  return layout(
    "Log in",
    `<h1>Log in</h1>
     ${contextLine(context)}
     <p class="lead">Choose your identity provider.</p>
     <ul class="tiles">${tiles}</ul>
     <p class="footer-link"><a href="/cancel">Cancel</a></p>`,
  );
}

function resultPage({ ok, title, detail, identity, provider, context }) {
  const icon = ok
    ? `<svg class="status ok" width="48" height="48" viewBox="0 0 32 32" aria-hidden="true"><path d="M16 2a14 14 0 1 0 14 14A14 14 0 0 0 16 2zm-2 19.59l-5-5L10.59 15 14 18.41 21.41 11 23 12.59z"/></svg>`
    : `<svg class="status err" width="48" height="48" viewBox="0 0 32 32" aria-hidden="true"><path d="M16 2a14 14 0 1 0 14 14A14 14 0 0 0 16 2zm-1.1 6h2.2v11h-2.2zM16 25a1.5 1.5 0 1 1 1.5-1.5A1.5 1.5 0 0 1 16 25z"/></svg>`;
  const rows = [
    ["User", identity],
    ["Identity provider", provider && (provider.name ?? provider.id)],
    ["Cluster", context?.cluster],
    ["Physical tenant", context?.physicalTenant],
    ["CLI context", ok ? context?.contextName : undefined],
  ].filter(([, v]) => v);
  return layout(
    ok ? "Logged in" : title,
    `${icon}
     <h1>${esc(ok ? "You're logged in" : title)}</h1>
     ${detail ? `<p class="${ok ? "lead" : "error"}">${esc(detail)}</p>` : ""}
     ${rows.length ? `<dl>${rows.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join("")}</dl>` : ""}
     <p class="lead">${ok ? "You can close this tab and return to the terminal." : "Return to the terminal to try again."}</p>`,
  );
}

function layout(title, body) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} | Camunda CLI</title>
<style>
  :root {
    --bg: #f4f4f4; --panel: #ffffff; --text: #161616; --muted: #525252; --border: #e0e0e0;
    --tile: #f4f4f4; --tile-hover: #e8e8e8; --link: #0f62fe; --focus: #0f62fe;
    --brand: #fc5d0d; --ok: #24a148; --err: #da1e28; --shell: #161616; --shell-text: #f4f4f4;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #161616; --panel: #262626; --text: #f4f4f4; --muted: #c6c6c6; --border: #393939;
      --tile: #393939; --tile-hover: #4c4c4c; --link: #78a9ff; --focus: #ffffff;
      --ok: #42be65; --err: #fa4d56; --shell: #000000;
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; background: var(--bg); color: var(--text);
    font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif; font-size: 14px; line-height: 1.43;
  }
  header {
    height: 48px; display: flex; align-items: center; gap: 12px; padding: 0 16px;
    background: var(--shell); color: var(--shell-text); border-bottom: 1px solid #393939;
  }
  .wordmark { color: var(--brand); font-weight: 600; font-size: 20px; letter-spacing: -0.02em; }
  .product { font-size: 14px; color: #c6c6c6; padding-left: 12px; border-left: 1px solid #525252; }
  main { max-width: 480px; margin: 64px auto; padding: 0 16px; }
  .panel { background: var(--panel); padding: 32px; border: 1px solid var(--border); }
  h1 { font-size: 28px; font-weight: 400; line-height: 1.29; margin: 0 0 8px; }
  .meta { color: var(--muted); margin: 0 0 24px; }
  .lead { margin: 16px 0; }
  .error {
    margin: 16px 0; padding: 12px 16px; border-left: 3px solid var(--err);
    background: color-mix(in srgb, var(--err) 8%, var(--panel)); word-break: break-word;
  }
  ul.tiles { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
  ul.tiles > li { min-width: 0; }
  .tile {
    display: flex; align-items: center; gap: 16px; padding: 16px; min-height: 64px;
    background: var(--tile); color: var(--text); text-decoration: none; outline: 2px solid transparent;
    transition: background 110ms cubic-bezier(0.2, 0, 0.38, 0.9);
  }
  .tile:hover { background: var(--tile-hover); }
  .tile:focus-visible { outline-color: var(--focus); outline-offset: -2px; }
  .badge {
    flex: none; width: 32px; height: 32px; display: grid; place-items: center;
    background: var(--brand); color: #ffffff; font-weight: 600;
  }
  .tile-text { flex: 1; display: flex; flex-direction: column; min-width: 0; }
  .tile-title { font-size: 16px; }
  .tile-sub { color: var(--muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .chevron { margin-left: auto; flex: none; fill: var(--link); }
  .footer-link { margin: 24px 0 0; }
  a { color: var(--link); }
  .status { display: block; margin-bottom: 16px; }
  .status.ok { fill: var(--ok); }
  .status.err { fill: var(--err); }
  dl { display: grid; grid-template-columns: max-content 1fr; gap: 8px 24px; margin: 24px 0; }
  dt { color: var(--muted); }
  dd { margin: 0; word-break: break-all; }
</style>
</head>
<body>
<header><span class="wordmark">camunda</span><span class="product">Command-line login</span></header>
<main><div class="panel">${body}</div></main>
</body>
</html>`;
}

async function refresh(config, userName, user) {
  if (!user.refreshToken) throw new Error("session expired - run: c8poc login");
  const tokens = await getJson(user.tokenEndpoint, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: user.refreshToken,
      client_id: user.clientId,
      ...(user.resource ? { resource: user.resource } : {}),
    }),
  }).catch(() => {
    throw new Error("session expired - run: c8poc login");
  });
  config.users[userName] = withTokens(user, tokens);
  saveConfig(config);
  return config.users[userName];
}

// Fails the login on a token the cluster would reject, with the reason, instead of letting the
// first API call end in a bare 401. Best effort: opaque (non-JWT) tokens are left to the cluster.
function checkAccessToken(accessToken, provider) {
  const claims = decodeJwt(accessToken);
  if (!claims.iss) return;
  const trim = (u) => String(u).replace(/\/$/, "");
  if (trim(claims.iss) !== trim(provider.issuer)) {
    throw new Error(
      `the access token was issued by ${claims.iss}, but the cluster trusts ${provider.issuer} for '${provider.id}'` +
        " (for Entra ID: set the API's requestedAccessTokenVersion to 2)",
    );
  }
  const expected = provider.audience ?? [];
  const actual = [].concat(claims.aud ?? []);
  if (expected.length && !actual.some((a) => expected.includes(a))) {
    const request = provider.resource ? `resource ${provider.resource}` : `scopes '${(provider.scopes ?? []).join(" ")}'`;
    throw new Error(
      `the access token has audience ${JSON.stringify(actual)}, but the cluster accepts ${JSON.stringify(expected)}.` +
        ` The identity provider did not grant the cluster's audience for ${request}:` +
        ` request the API's scope (cli-clients.${provider.id}.scopes) or resource (cli-clients.${provider.id}.resource),` +
        ` or add an audience mapper at the identity provider`,
    );
  }
}

// ---------- commands ----------

async function login(args, contextFlag) {
  const providerId = takeFlag(args, "--provider", true);
  const noBrowser = takeFlag(args, "--no-browser", false);
  const physicalTenantFlag = takeFlag(args, "--physical-tenant", true);
  const nameFlag = takeFlag(args, "--name", true) ?? contextFlag;
  const config = loadConfig();
  // Without a url, log in to a configured context: the named one, else the current one.
  const existingName = !args[0] && !physicalTenantFlag ? (nameFlag ?? config.currentContext) : undefined;
  const existing = existingName && config.contexts[existingName];
  const { clusterUrl, physicalTenant } = existing
    ? { clusterUrl: config.clusters[existing.cluster]?.server, physicalTenant: existing.physicalTenant }
    : parseTarget(args[0] ?? DEFAULT_CLUSTER, physicalTenantFlag);
  if (!clusterUrl) throw new Error(`context '${existingName}' has no cluster - see: c8poc config set-context`);

  const discovery = await discover(clusterUrl, physicalTenant ?? (await choosePhysicalTenant(clusterUrl)));
  const target = {
    clusterUrl,
    physicalTenant: discovery.physical_tenant ?? physicalTenant ?? "default",
    apiBase: discovery.api_base ?? "",
  };
  const name = existing ? existingName : (nameFlag ?? contextName(clusterUrl, target.physicalTenant));
  const where = `${clusterUrl}${target.apiBase ? ` (physical tenant ${target.physicalTenant})` : ""}`;

  let user;
  if (discovery.method === "none") {
    user = { method: "none" };
  } else if (discovery.method === "basic") {
    const username = await prompt("Username: ");
    const password = await prompt("Password: ");
    user = { method: "basic", username, basic: Buffer.from(`${username}:${password}`).toString("base64") };
    const res = await fetch(`${clusterUrl}${target.apiBase}/v2/topology`, { headers: authHeaders(user) });
    if (!res.ok) throw new Error(`login failed: ${res.status}`);
  } else if (discovery.method === "oidc") {
    if (!discovery.providers?.length) throw new Error(`${where} announces no CLI client for any identity provider`);
    const { provider, meta, tokens } = await browserLogin(discovery.providers, {
      providerId,
      noBrowser,
      cluster: clusterUrl,
      physicalTenant: target.physicalTenant,
      contextName: name,
    });
    user = withTokens(
      { method: "oidc", provider: provider.id, issuer: provider.issuer, clientId: provider.client_id, tokenEndpoint: meta.token_endpoint, resource: provider.resource },
      tokens,
    );
  } else {
    throw new Error(`unsupported authentication method '${discovery.method}'`);
  }

  addLogin(config, name, target, user);
  config.currentContext = name;
  saveConfig(config);
  const how = user.method === "oidc" ? ` via ${user.provider} (${user.issuer})` : user.method === "basic" ? " (basic auth)" : "";
  info(`Logged in to ${where} as ${identity(user) ?? "anonymous"}${how}.`);
  info(`Context '${name}' is current.`);
}

// RFC 9728 style: a physical tenant's document sits at the well-known path plus its API path
async function discover(clusterUrl, physicalTenant) {
  const url = `${clusterUrl}/.well-known/camunda-auth${physicalTenant ? `/physical-tenants/${physicalTenant}` : ""}`;
  return getJson(url).catch((e) => {
    throw new Error(e.message.includes("-> 404") ? `${clusterUrl} has no physical tenant '${physicalTenant}'` : e.message);
  });
}

// Asks for a physical tenant if the cluster lists them (opt-in on the cluster); otherwise the
// default physical tenant, whose document is the unsuffixed one.
async function choosePhysicalTenant(clusterUrl) {
  const physicalTenants = (await discover(clusterUrl)).physical_tenants ?? [];
  if (physicalTenants.length < 2) return undefined;
  physicalTenants.forEach((t, i) => info(`  [${i + 1}] ${t.id}${t.name ? `  (${t.name})` : ""}`));
  const chosen = physicalTenants[Number(await prompt("Physical tenant: ")) - 1];
  if (!chosen) throw new Error("invalid choice");
  return chosen.id === "default" ? undefined : chosen.id;
}

async function listPhysicalTenants(url) {
  const config = loadConfig();
  const current = config.contexts[config.currentContext];
  const { clusterUrl } = parseTarget(url ?? config.clusters[current?.cluster]?.server ?? DEFAULT_CLUSTER);
  const physicalTenants = (await discover(clusterUrl)).physical_tenants;
  if (!physicalTenants) {
    return info(`${clusterUrl} does not list its physical tenants - log in with the physical tenant's url or --physical-tenant <id>`);
  }
  const table = [["PHYSICAL TENANT", "NAME", "LOGIN"], ...physicalTenants.map((t) => [t.id, t.name ?? "", t.id === "default" ? clusterUrl : `${clusterUrl}/physical-tenants/${t.id}`])];
  const widths = table[0].map((_, i) => Math.max(...table.map((r) => r[i].length)));
  for (const row of table) console.log(row.map((c, i) => c.padEnd(widths[i])).join("  ").trimEnd());
}

function authHeaders(user) {
  if (user.method === "basic") return { authorization: `Basic ${user.basic}` };
  if (user.method === "oidc") return { authorization: `Bearer ${user.accessToken}` };
  return {};
}

async function call(path, contextFlag) {
  const config = loadConfig();
  const resolved = resolveContext(config, contextFlag);
  let user = resolved.user;
  if (user.method === "oidc" && user.expiresAt - REFRESH_LEEWAY_SECONDS < Date.now() / 1000) {
    user = await refresh(config, resolved.context.user, user);
  }
  const url = `${resolved.clusterUrl}${resolved.context.apiBase ?? ""}${path.startsWith("/") ? path : `/${path}`}`;
  const res = await fetch(url, { headers: authHeaders(user) });
  const body = await res.text();
  if (res.status === 401) throw new Error(`401 Unauthorized on ${url} - run: c8poc login`);
  try {
    console.log(JSON.stringify(JSON.parse(body), null, 2));
  } catch {
    console.log(body);
  }
  if (!res.ok) process.exitCode = 1;
}

function whoami(contextFlag) {
  const { name, context, clusterUrl, user } = resolveContext(loadConfig(), contextFlag);
  const out = { context: name, cluster: clusterUrl, physicalTenant: context.physicalTenant, apiBase: context.apiBase || "/", user: context.user, method: user.method, identity: identity(user) };
  if (user.method === "oidc") {
    Object.assign(out, {
      provider: user.provider,
      issuer: user.issuer,
      client: user.clientId,
      audience: decodeJwt(user.accessToken).aud,
      accessTokenExpires: new Date(user.expiresAt * 1000).toISOString(),
      refreshToken: user.refreshToken ? "present" : "none",
    });
  }
  console.log(JSON.stringify(out, null, 2));
}

function logout(contextFlag) {
  const config = loadConfig();
  const { name } = resolveContext(config, contextFlag);
  deleteContext(config, name);
  saveConfig(config);
  info(`Logged out: deleted context '${name}' and its user.`);
}

// ---------- config (kubectl config) ----------

function printTable(header, rows) {
  const table = [header, ...rows];
  const widths = header.map((_, i) => Math.max(...table.map((r) => String(r[i] ?? "").length)));
  for (const row of table) console.log(row.map((c, i) => String(c ?? "").padEnd(widths[i])).join("   ").trimEnd());
}

function deleteContext(config, name) {
  const context = config.contexts[name];
  if (!context) throw new Error(`no context '${name}' - see: c8poc config get-contexts`);
  delete config.contexts[name];
  deleteUserIfUnused(config, context.user);
  deleteClusterIfUnused(config, context.cluster);
  if (config.currentContext === name) config.currentContext = undefined;
}

const configCommands = {
  discover: async ([url]) => {
    const config = loadConfig();
    const current = config.contexts[config.currentContext];
    const { clusterUrl } = parseTarget(url ?? config.clusters[current?.cluster]?.server ?? DEFAULT_CLUSTER);
    const physicalTenants = (await discover(clusterUrl)).physical_tenants ?? [{ id: "default" }];
    const rows = [];
    for (const { id, name: displayName } of physicalTenants) {
      const name = contextName(clusterUrl, id);
      if (config.contexts[name]) {
        rows.push([name, id, displayName, "exists"]);
        continue;
      }
      const document = await discover(clusterUrl, id === "default" ? undefined : id);
      setContext(config, name, { clusterUrl, physicalTenant: id, apiBase: document.api_base ?? "" }, undefined);
      rows.push([name, id, displayName, "added"]);
    }
    saveConfig(config);
    printTable(["CONTEXT", "PHYSICAL TENANT", "NAME", ""], rows);
    info(`\nLog in with: c8poc --context <context> login   (or: c8poc config use-context <context>; c8poc login)`);
  },
  "set-context": async (args) => {
    const name = args.shift();
    const cluster = takeFlag(args, "--cluster", true);
    const physicalTenantFlag = takeFlag(args, "--physical-tenant", true);
    if (!name || (!cluster && !physicalTenantFlag)) {
      throw new Error("usage: c8poc config set-context <name> [--cluster <url>] [--physical-tenant <id>]");
    }
    const config = loadConfig();
    const existing = config.contexts[name];
    const clusterUrl = parseTarget(cluster ?? config.clusters[existing?.cluster]?.server ?? DEFAULT_CLUSTER).clusterUrl;
    const physicalTenant = physicalTenantFlag ?? existing?.physicalTenant ?? "default";
    const document = await discover(clusterUrl, physicalTenant === "default" ? undefined : physicalTenant);
    // a user is only valid for the cluster and physical tenant it logged in to
    const keepsUser = existing && existing.cluster === new URL(clusterUrl).host && existing.physicalTenant === physicalTenant;
    setContext(config, name, { clusterUrl, physicalTenant, apiBase: document.api_base ?? "" }, keepsUser ? existing.user : undefined);
    saveConfig(config);
    console.log(`Context "${name}" ${existing ? "modified" : "created"}.${keepsUser ? "" : ` Log in with: c8poc --context ${name} login`}`);
  },
  "get-contexts": () => {
    const config = loadConfig();
    printTable(
      ["CURRENT", "NAME", "CLUSTER", "PHYSICAL TENANT", "USER"],
      Object.entries(config.contexts).map(([name, c]) => [name === config.currentContext ? "*" : "", name, c.cluster, c.physicalTenant, c.user ?? "(not logged in)"]),
    );
  },
  "current-context": () => {
    const current = loadConfig().currentContext;
    if (!current) throw new Error("current-context is not set");
    console.log(current);
  },
  "use-context": ([name]) => {
    if (!name) throw new Error("usage: c8poc config use-context <name>");
    const config = loadConfig();
    if (!config.contexts[name]) throw new Error(`no context '${name}' - see: c8poc config get-contexts`);
    config.currentContext = name;
    saveConfig(config);
    console.log(`Switched to context "${name}".`);
  },
  "rename-context": ([from, to]) => {
    if (!from || !to) throw new Error("usage: c8poc config rename-context <old> <new>");
    const config = loadConfig();
    if (!config.contexts[from]) throw new Error(`no context '${from}' - see: c8poc config get-contexts`);
    if (config.contexts[to]) throw new Error(`context '${to}' already exists`);
    config.contexts[to] = config.contexts[from];
    delete config.contexts[from];
    if (config.currentContext === from) config.currentContext = to;
    saveConfig(config);
    console.log(`Context "${from}" renamed to "${to}".`);
  },
  "delete-context": ([name]) => {
    if (!name) throw new Error("usage: c8poc config delete-context <name>");
    const config = loadConfig();
    deleteContext(config, name);
    saveConfig(config);
    console.log(`Deleted context ${name} from ${CONFIG_FILE}`);
  },
  "get-clusters": () => printTable(["NAME", "SERVER"], Object.entries(loadConfig().clusters).map(([n, c]) => [n, c.server])),
  "get-users": () =>
    printTable(
      ["NAME", "METHOD", "PROVIDER", "IDENTITY"],
      Object.entries(loadConfig().users).map(([n, u]) => [n, u.method, u.provider ?? "", identity(u) ?? ""]),
    ),
  view: () => {
    const config = loadConfig();
    const redact = (user) =>
      Object.fromEntries(Object.entries(user).map(([k, v]) => [k, ["accessToken", "refreshToken", "basic"].includes(k) ? "REDACTED" : v]));
    const users = Object.fromEntries(Object.entries(config.users).map(([n, u]) => [n, redact(u)]));
    console.log(JSON.stringify({ ...config, users }, null, 2));
  },
};

// ---------- main ----------

const argv = process.argv.slice(2);
const contextFlag = takeFlag(argv, "--context", true); // global, like kubectl --context
const [command, ...args] = argv;
const commands = {
  login: () => login(args, contextFlag),
  logout: () => logout(contextFlag),
  "physical-tenants": () => listPhysicalTenants(args[0]),
  whoami: () => whoami(contextFlag),
  topology: () => call("/v2/topology", contextFlag),
  api: () => {
    if (!args[0]) throw new Error("usage: c8poc api <path>");
    return call(args[0], contextFlag);
  },
  config: () => {
    const sub = configCommands[args[0]];
    if (!sub) throw new Error(`usage: c8poc config ${Object.keys(configCommands).join("|")}`);
    return sub(args.slice(1));
  },
};

if (!commands[command]) {
  console.error(USAGE);
  process.exit(command && command !== "help" && command !== "--help" ? 1 : 0);
}

try {
  await commands[command]();
} catch (e) {
  console.error("✖", e.message);
  process.exit(1);
}
// Nothing may outlive a command (e.g. a connection the browser still holds open) - but let
// stdout drain first: writes to a pipe are asynchronous on macOS.
process.stdout.write("", () => process.exit(process.exitCode ?? 0));
