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
      --provider <id>            Identity provider (instead of being asked)
      --name <context>           Context name (default: <host>[/<physical tenant>]);
                                 c8poc --context <name> login ... works as well
      --no-browser               Only print the login URL
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
    const provider = await chooseProvider(discovery.providers, providerId);
    const meta = await getJson(`${provider.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`);
    const tokens = await browserLogin(meta, provider, { noBrowser });
    checkAccessToken(tokens.access_token, provider);
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
