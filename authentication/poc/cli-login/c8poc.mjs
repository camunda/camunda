#!/usr/bin/env node
// PoC of a c8ctl-style CLI that logs in with nothing but the cluster URL:
//   1. GET <cluster>/.well-known/camunda-auth          -> auth method, issuer, public client
//   2. GET <issuer>/.well-known/openid-configuration   -> authorize / token endpoints
//   3. authorization code + PKCE in the browser, redirected to a random 127.0.0.1 port (RFC 8252)
//   4. later commands call the cluster API with the stored token, refreshing it when it expires
//
// Physical tenants authenticate independently (own identity providers, clients, audiences), so
// every login creates a context - cluster + physical tenant + identity + tokens - like kubectl
// contexts: you can be logged in to pt1 via one identity provider and to pt2 via another, and
// switch between them.
//
// Zero dependencies; needs Node 18+. Contexts live in ~/.c8poc/session.json.

import { execFile } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { homedir } from "node:os";
import { join } from "node:path";
import { createInterface } from "node:readline/promises";

const SESSION_DIR = process.env.C8POC_HOME ?? join(homedir(), ".c8poc");
const SESSION_FILE = join(SESSION_DIR, "session.json");
const DEFAULT_CLUSTER = "http://localhost:8080";
const REFRESH_LEEWAY_SECONDS = 30;

const USAGE = `Usage: c8poc <command> [options]

Commands:
  login [url]            Log in and make it the current context; OIDC opens the browser.
                         url: the cluster (default ${DEFAULT_CLUSTER}), or a physical
                         tenant, e.g. ${DEFAULT_CLUSTER}/physical-tenants/pt1
      --tenant <id>      Physical tenant (instead of putting it in the url)
      --provider <id>    Pick an identity provider instead of being asked
      --context <name>   Context name (default: <host>[/<tenant>])
      --no-browser       Only print the login URL
  contexts               List contexts (* = current)
  use <context>          Switch the current context
  whoami                 Show the current context's cluster, tenant and identity
  topology               GET /v2/topology on the context's physical tenant
  api <path>             GET any path below the tenant's API, e.g. c8poc api /v2/license
  logout [<context>]     Forget the current (or the named) context; --all forgets all

whoami, topology, api and logout take --context <name> to use another than the current one.
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

// ---------- contexts ----------

function loadStore() {
  let raw;
  try {
    raw = JSON.parse(readFileSync(SESSION_FILE, "utf8"));
  } catch {
    return { current: undefined, contexts: {} };
  }
  if (raw.contexts) return raw;
  // single session of an earlier c8poc version: keep it as a context of the default tenant
  const name = contextName(raw.clusterUrl, undefined);
  return { current: name, contexts: { [name]: { ...raw, tenant: "default", apiBase: "" } } };
}

function saveStore(store) {
  mkdirSync(SESSION_DIR, { recursive: true, mode: 0o700 });
  writeFileSync(SESSION_FILE, JSON.stringify(store, null, 2), { mode: 0o600 });
}

function saveContext(name, context, { makeCurrent }) {
  const store = loadStore();
  store.contexts[name] = context;
  if (makeCurrent) store.current = name;
  saveStore(store);
}

function requireContext(name) {
  const store = loadStore();
  const resolved = name ?? store.current;
  if (!resolved) throw new Error("not logged in - run: c8poc login [url]");
  const context = store.contexts[resolved];
  if (!context) throw new Error(`no context '${resolved}' - see: c8poc contexts`);
  return { name: resolved, context };
}

function contextName(clusterUrl, tenant) {
  return new URL(clusterUrl).host + (tenant && tenant !== "default" ? `/${tenant}` : "");
}

// "https://oc/physical-tenants/pt1" -> cluster "https://oc", tenant "pt1"
function parseTarget(url, tenantFlag) {
  const trimmed = url.replace(/\/$/, "");
  const match = trimmed.match(/^(.*)\/physical-tenants\/([^/]+)$/);
  if (match && tenantFlag && tenantFlag !== match[2]) {
    throw new Error(`--tenant ${tenantFlag} contradicts the url's tenant ${match[2]}`);
  }
  return match ? { clusterUrl: match[1], tenant: match[2] } : { clusterUrl: trimmed, tenant: tenantFlag };
}

function withTokens(session, tokens) {
  return {
    ...session,
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token ?? session.refreshToken,
    expiresAt: Math.floor(Date.now() / 1000) + (tokens.expires_in ?? 300),
  };
}

// ---------- OIDC ----------

async function chooseProvider(providers, providerId) {
  if (providerId) {
    const p = providers.find((x) => x.id === providerId);
    if (!p) throw new Error(`no provider '${providerId}', available: ${providers.map((x) => x.id).join(", ")}`);
    return p;
  }
  if (providers.length === 1) return providers[0];
  providers.forEach((p, i) => info(`  [${i + 1}] ${p.id}  (${p.issuer})`));
  const p = providers[Number(await prompt("Log in with: ")) - 1];
  if (!p) throw new Error("invalid choice");
  return p;
}

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

// Waits for the authorization server to redirect the browser back to the loopback address(es).
async function startCallbackServer(redirect, expectedState) {
  let resolveCode, rejectCode;
  const code = new Promise((res, rej) => ((resolveCode = res), (rejectCode = rej)));
  const handler = (req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    if (url.pathname !== (redirect.path || "/")) return res.writeHead(404).end();
    const error = url.searchParams.get("error");
    const ok = !error && url.searchParams.get("state") === expectedState;
    res.writeHead(ok ? 200 : 400, { "content-type": "text/html" });
    res.end(ok ? "<h3>Logged in to c8poc. You can close this tab.</h3>" : `<h3>Login failed: ${error ?? "state mismatch"}</h3>`);
    ok ? resolveCode(url.searchParams.get("code")) : rejectCode(new Error(error ?? "state mismatch"));
  };

  // The first address picks the port (unless fixed); the others must get the same one. If one is
  // taken there, start over with a new port.
  for (let attempt = 1; ; attempt++) {
    const servers = [];
    try {
      let port = redirect.fixedPort ?? 0;
      for (const address of redirect.addresses) {
        const server = createServer(handler);
        servers.push(server);
        port = await listen(server, port, address);
      }
      return { port, code, close: () => servers.forEach((s) => s.close()) };
    } catch (e) {
      servers.forEach((s) => s.close());
      if (e.code !== "EADDRINUSE" || redirect.fixedPort || attempt >= BIND_ATTEMPTS) {
        throw new Error(`cannot listen for the login redirect on ${redirect.host}: ${e.message}`);
      }
    }
  }
}

async function browserLogin(meta, provider, { noBrowser }) {
  const verifier = base64url(randomBytes(32));
  const challenge = base64url(createHash("sha256").update(verifier).digest());
  const state = base64url(randomBytes(16));

  const redirect = parseRedirectUri(provider.redirect_uri);
  const callback = await startCallbackServer(redirect, state);
  const redirectUri = `http://${redirect.host}:${callback.port}${redirect.path}`;

  const authorize = new URL(meta.authorization_endpoint);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: provider.client_id,
    redirect_uri: redirectUri,
    scope: (provider.scopes?.length ? provider.scopes : ["openid"]).join(" "),
    state,
    code_challenge: challenge,
    code_challenge_method: "S256",
    ...(provider.resource ? { resource: provider.resource } : {}),
  });

  if (noBrowser) {
    info(`Open this URL to log in:\n\n  ${authorize}\n`);
  } else {
    info("Opening the browser to log in... (if it does not open, use --no-browser)");
    openBrowser(authorize.toString());
  }

  let code;
  try {
    code = await callback.code;
  } finally {
    callback.close();
  }

  return getJson(meta.token_endpoint, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: redirectUri,
      client_id: provider.client_id,
      code_verifier: verifier,
      ...(provider.resource ? { resource: provider.resource } : {}),
    }),
  });
}

async function refresh(name, session) {
  if (!session.refreshToken) throw new Error("session expired - run: c8poc login");
  const tokens = await getJson(session.tokenEndpoint, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: session.refreshToken,
      client_id: session.clientId,
      ...(session.resource ? { resource: session.resource } : {}),
    }),
  }).catch(() => {
    throw new Error("session expired - run: c8poc login");
  });
  const refreshed = withTokens(session, tokens);
  saveContext(name, refreshed, { makeCurrent: false });
  return refreshed;
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

async function login(args) {
  const providerId = takeFlag(args, "--provider", true);
  const noBrowser = takeFlag(args, "--no-browser", false);
  const tenantFlag = takeFlag(args, "--tenant", true);
  const nameFlag = takeFlag(args, "--context", true);
  const store = loadStore();
  const defaultUrl = store.contexts[store.current]?.clusterUrl ?? DEFAULT_CLUSTER;
  const { clusterUrl, tenant } = parseTarget(args[0] ?? defaultUrl, tenantFlag);

  // RFC 9728 style: the tenant's document sits at the well-known path plus the tenant's path
  const discoveryUrl = `${clusterUrl}/.well-known/camunda-auth${tenant ? `/physical-tenants/${tenant}` : ""}`;
  const discovery = await getJson(discoveryUrl).catch((e) => {
    throw new Error(e.message.includes("-> 404") ? `${clusterUrl} has no physical tenant '${tenant}'` : e.message);
  });
  const target = {
    clusterUrl,
    tenant: discovery.physical_tenant ?? tenant ?? "default",
    apiBase: discovery.api_base ?? "",
  };
  const name = nameFlag ?? contextName(clusterUrl, tenant);
  const where = `${clusterUrl}${target.apiBase ? ` (physical tenant ${target.tenant})` : ""}`;

  if (discovery.method === "none") {
    saveContext(name, { ...target, method: "none" }, { makeCurrent: true });
    info(`${where} does not require authentication. Context '${name}' is current.`);
    return;
  }

  if (discovery.method === "basic") {
    const username = await prompt("Username: ");
    const password = await prompt("Password: ");
    const context = { ...target, method: "basic", username, basic: Buffer.from(`${username}:${password}`).toString("base64") };
    const res = await fetch(`${clusterUrl}${target.apiBase}/v2/topology`, { headers: authHeaders(context) });
    if (!res.ok) throw new Error(`login failed: ${res.status}`);
    saveContext(name, context, { makeCurrent: true });
    info(`Logged in to ${where} as ${username} (basic auth). Context '${name}' is current.`);
    return;
  }

  if (discovery.method !== "oidc") throw new Error(`unsupported authentication method '${discovery.method}'`);
  if (!discovery.providers?.length) throw new Error(`${where} announces no CLI client for any identity provider`);

  const provider = await chooseProvider(discovery.providers, providerId);
  const meta = await getJson(`${provider.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`);
  const tokens = await browserLogin(meta, provider, { noBrowser });
  checkAccessToken(tokens.access_token, provider);

  const context = withTokens(
    { ...target, method: "oidc", provider: provider.id, issuer: provider.issuer, clientId: provider.client_id, tokenEndpoint: meta.token_endpoint, resource: provider.resource },
    tokens,
  );
  saveContext(name, context, { makeCurrent: true });
  const claims = decodeJwt(context.accessToken);
  info(`Logged in to ${where} as ${claims.preferred_username ?? claims.sub} via ${provider.id} (${provider.issuer}). Context '${name}' is current.`);
}

function authHeaders(context) {
  if (context.method === "basic") return { authorization: `Basic ${context.basic}` };
  if (context.method === "oidc") return { authorization: `Bearer ${context.accessToken}` };
  return {};
}

async function call(path, contextFlag) {
  let { name, context } = requireContext(contextFlag);
  if (context.method === "oidc" && context.expiresAt - REFRESH_LEEWAY_SECONDS < Date.now() / 1000) {
    context = await refresh(name, context);
  }
  const url = `${context.clusterUrl}${context.apiBase ?? ""}${path.startsWith("/") ? path : `/${path}`}`;
  const res = await fetch(url, { headers: authHeaders(context) });
  const body = await res.text();
  if (res.status === 401) throw new Error(`401 Unauthorized on ${url} - run: c8poc login`);
  try {
    console.log(JSON.stringify(JSON.parse(body), null, 2));
  } catch {
    console.log(body);
  }
  if (!res.ok) process.exitCode = 1;
}

function describe(name, context) {
  const out = { context: name, cluster: context.clusterUrl, physicalTenant: context.tenant ?? "default", apiBase: context.apiBase || "/", method: context.method };
  if (context.method === "basic") out.user = context.username;
  if (context.method === "oidc") {
    const claims = decodeJwt(context.accessToken);
    Object.assign(out, {
      user: claims.preferred_username ?? claims.sub,
      provider: context.provider,
      issuer: context.issuer,
      client: context.clientId,
      audience: claims.aud,
      accessTokenExpires: new Date(context.expiresAt * 1000).toISOString(),
      refreshToken: context.refreshToken ? "present" : "none",
    });
  }
  return out;
}

function whoami(contextFlag) {
  const { name, context } = requireContext(contextFlag);
  console.log(JSON.stringify(describe(name, context), null, 2));
}

function contexts() {
  const store = loadStore();
  const rows = Object.entries(store.contexts).map(([name, context]) => {
    const d = describe(name, context);
    return [name === store.current ? "*" : "", name, d.physicalTenant, d.provider ?? d.method, d.user ?? "", d.accessTokenExpires ?? ""];
  });
  if (!rows.length) return info("No contexts - run: c8poc login [url]");
  const table = [["", "CONTEXT", "TENANT", "PROVIDER", "USER", "ACCESS TOKEN EXPIRES"], ...rows];
  const widths = table[0].map((_, i) => Math.max(...table.map((r) => String(r[i]).length)));
  for (const row of table) console.log(row.map((c, i) => String(c).padEnd(widths[i])).join("  ").trimEnd());
}

function use(name) {
  if (!name) throw new Error("usage: c8poc use <context>");
  const store = loadStore();
  if (!store.contexts[name]) throw new Error(`no context '${name}' - see: c8poc contexts`);
  store.current = name;
  saveStore(store);
  info(`Context '${name}' is current.`);
}

function logout(args, contextFlag) {
  const store = loadStore();
  if (takeFlag(args, "--all", false)) {
    rmSync(SESSION_FILE, { force: true });
    return info("Logged out of all contexts.");
  }
  const { name } = requireContext(args[0] ?? contextFlag);
  delete store.contexts[name];
  if (store.current === name) store.current = undefined;
  saveStore(store);
  info(`Logged out of '${name}'.${store.current ? "" : " No context is current - see: c8poc contexts / c8poc use"}`);
}

// ---------- main ----------

const [command, ...args] = process.argv.slice(2);
const contextFlag = command === "login" ? undefined : takeFlag(args, "--context", true);
const commands = {
  login: () => login(args),
  contexts: () => contexts(),
  use: () => use(args[0]),
  whoami: () => whoami(contextFlag),
  topology: () => call("/v2/topology", contextFlag),
  api: () => {
    if (!args[0]) throw new Error("usage: c8poc api <path>");
    return call(args[0], contextFlag);
  },
  logout: () => logout(args, contextFlag),
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
