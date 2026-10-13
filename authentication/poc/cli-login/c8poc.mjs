#!/usr/bin/env node
// PoC of a c8ctl-style CLI that logs in with nothing but the cluster URL:
//   1. GET <cluster>/.well-known/camunda-auth          -> auth method, issuer, public client
//   2. GET <issuer>/.well-known/openid-configuration   -> authorize / token endpoints
//   3. authorization code + PKCE in the browser, redirected to a random 127.0.0.1 port (RFC 8252)
//   4. later commands call the cluster API with the stored token, refreshing it when it expires
//
// Zero dependencies; needs Node 18+. The session lives in ~/.c8poc/session.json.

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
  login [clusterUrl]     Log in (default ${DEFAULT_CLUSTER}); OIDC opens the browser
      --provider <id>    Pick an OIDC provider instead of being asked
      --no-browser       Only print the login URL
  whoami                 Show the cluster and the logged-in identity
  topology               GET /v2/topology
  api <path>             GET any cluster path, e.g. c8poc api /v2/topology
  logout                 Forget the session
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

// ---------- session ----------

function loadSession() {
  try {
    return JSON.parse(readFileSync(SESSION_FILE, "utf8"));
  } catch {
    return undefined;
  }
}

function saveSession(session) {
  mkdirSync(SESSION_DIR, { recursive: true, mode: 0o700 });
  writeFileSync(SESSION_FILE, JSON.stringify(session, null, 2), { mode: 0o600 });
}

function requireSession() {
  const session = loadSession();
  if (!session) throw new Error("not logged in - run: c8poc login [clusterUrl]");
  return session;
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

async function refresh(session) {
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
  saveSession(refreshed);
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
  const clusterUrl = (args[0] ?? loadSession()?.clusterUrl ?? DEFAULT_CLUSTER).replace(/\/$/, "");

  const discovery = await getJson(`${clusterUrl}/.well-known/camunda-auth`);

  if (discovery.method === "none") {
    saveSession({ clusterUrl, method: "none" });
    info(`${clusterUrl} does not require authentication.`);
    return;
  }

  if (discovery.method === "basic") {
    const username = await prompt("Username: ");
    const password = await prompt("Password: ");
    const session = { clusterUrl, method: "basic", username, basic: Buffer.from(`${username}:${password}`).toString("base64") };
    const res = await fetch(`${clusterUrl}/v2/topology`, { headers: authHeaders(session) });
    if (!res.ok) throw new Error(`login failed: ${res.status}`);
    saveSession(session);
    info(`Logged in to ${clusterUrl} as ${username} (basic auth).`);
    return;
  }

  if (discovery.method !== "oidc") throw new Error(`unsupported authentication method '${discovery.method}'`);
  if (!discovery.providers?.length) throw new Error(`${clusterUrl} announces no CLI client for any identity provider`);

  const provider = await chooseProvider(discovery.providers, providerId);
  const meta = await getJson(`${provider.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`);
  const tokens = await browserLogin(meta, provider, { noBrowser });
  checkAccessToken(tokens.access_token, provider);

  const session = withTokens(
    { clusterUrl, method: "oidc", provider: provider.id, issuer: provider.issuer, clientId: provider.client_id, tokenEndpoint: meta.token_endpoint, resource: provider.resource },
    tokens,
  );
  saveSession(session);
  const claims = decodeJwt(session.accessToken);
  info(`Logged in to ${clusterUrl} as ${claims.preferred_username ?? claims.sub} via ${provider.id} (${provider.issuer}).`);
}

function authHeaders(session) {
  if (session.method === "basic") return { authorization: `Basic ${session.basic}` };
  if (session.method === "oidc") return { authorization: `Bearer ${session.accessToken}` };
  return {};
}

async function call(path) {
  let session = requireSession();
  if (session.method === "oidc" && session.expiresAt - REFRESH_LEEWAY_SECONDS < Date.now() / 1000) {
    session = await refresh(session);
  }
  const res = await fetch(`${session.clusterUrl}${path.startsWith("/") ? path : `/${path}`}`, { headers: authHeaders(session) });
  const body = await res.text();
  if (res.status === 401) throw new Error("401 Unauthorized - run: c8poc login");
  try {
    console.log(JSON.stringify(JSON.parse(body), null, 2));
  } catch {
    console.log(body);
  }
  if (!res.ok) process.exitCode = 1;
}

function whoami() {
  const session = requireSession();
  const out = { cluster: session.clusterUrl, method: session.method };
  if (session.method === "basic") out.user = session.username;
  if (session.method === "oidc") {
    const claims = decodeJwt(session.accessToken);
    Object.assign(out, {
      user: claims.preferred_username ?? claims.sub,
      provider: session.provider,
      issuer: session.issuer,
      client: session.clientId,
      audience: claims.aud,
      accessTokenExpires: new Date(session.expiresAt * 1000).toISOString(),
      refreshToken: session.refreshToken ? "present" : "none",
    });
  }
  console.log(JSON.stringify(out, null, 2));
}

function logout() {
  rmSync(SESSION_FILE, { force: true });
  info("Logged out.");
}

// ---------- main ----------

const [command, ...args] = process.argv.slice(2);
const commands = {
  login: () => login(args),
  whoami: () => whoami(),
  topology: () => call("/v2/topology"),
  api: () => {
    if (!args[0]) throw new Error("usage: c8poc api <path>");
    return call(args[0]);
  },
  logout: () => logout(),
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
