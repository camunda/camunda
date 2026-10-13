#!/usr/bin/env node
// PoC of a c8ctl-style login that needs nothing but the cluster URL:
//   1. GET <cluster>/.well-known/camunda-auth          -> auth method, issuer, public client
//   2. GET <issuer>/.well-known/openid-configuration   -> authorize / token endpoints
//   3. authorization code + PKCE in the browser, redirected to a random 127.0.0.1 port (RFC 8252)
//   4. call the cluster API with the access token
//
// Usage: node c8ctl-login.mjs [clusterUrl] [apiPath] [--provider <id>] [--no-browser]
// Zero dependencies; needs Node 18+.

import { execFile } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { createInterface } from "node:readline/promises";

const args = process.argv.slice(2);
const flag = (name) => {
  const i = args.indexOf(name);
  if (i < 0) return undefined;
  const [, value] = args.splice(i, 2);
  return value;
};
const providerId = flag("--provider");
const noBrowser = args.includes("--no-browser") && args.splice(args.indexOf("--no-browser"), 1);
const clusterUrl = (args[0] ?? "http://localhost:8080").replace(/\/$/, "");
const apiPath = args[1] ?? "/v2/topology";

const log = (...m) => console.error("»", ...m);

async function getJson(url, init) {
  const res = await fetch(url, init);
  const body = await res.text();
  if (!res.ok) throw new Error(`${init?.method ?? "GET"} ${url} -> ${res.status}: ${body}`);
  return JSON.parse(body);
}

const base64url = (buf) => buf.toString("base64url");

function openBrowser(url) {
  const cmd = { darwin: "open", win32: "explorer" }[process.platform] ?? "xdg-open";
  execFile(cmd, [url], () => {});
}

function decodeJwt(token) {
  try {
    return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString());
  } catch {
    return undefined;
  }
}

async function choose(providers) {
  if (providerId) {
    const p = providers.find((x) => x.id === providerId);
    if (!p) throw new Error(`no provider '${providerId}' in ${providers.map((x) => x.id)}`);
    return p;
  }
  if (providers.length === 1) return providers[0];
  providers.forEach((p, i) => console.error(`  [${i + 1}] ${p.id}  (${p.issuer})`));
  const rl = createInterface({ input: process.stdin, output: process.stderr });
  const answer = await rl.question("Log in with: ");
  rl.close();
  const p = providers[Number(answer) - 1];
  if (!p) throw new Error("invalid choice");
  return p;
}

// Waits for the authorization server to redirect the browser back to us.
function startCallbackServer(expectedState) {
  let resolveCode, rejectCode;
  const code = new Promise((res, rej) => ((resolveCode = res), (rejectCode = rej)));
  const server = createServer((req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    if (url.pathname !== "/callback") return res.writeHead(404).end();
    const error = url.searchParams.get("error");
    const ok = !error && url.searchParams.get("state") === expectedState;
    res.writeHead(ok ? 200 : 400, { "content-type": "text/html" });
    res.end(ok ? "<h3>Logged in. You can close this tab.</h3>" : `<h3>Login failed: ${error ?? "state mismatch"}</h3>`);
    ok ? resolveCode(url.searchParams.get("code")) : rejectCode(new Error(error ?? "state mismatch"));
  });
  const listening = new Promise((res) => server.listen(0, "127.0.0.1", () => res(server.address().port)));
  return { listening, code, close: () => server.close() };
}

async function oidcLogin(provider) {
  const meta = await getJson(`${provider.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`);
  log("authorization server:", meta.issuer);

  const verifier = base64url(randomBytes(32));
  const challenge = base64url(createHash("sha256").update(verifier).digest());
  const state = base64url(randomBytes(16));

  const callback = startCallbackServer(state);
  const port = await callback.listening;
  const redirectUri = `http://127.0.0.1:${port}/callback`;

  const authorize = new URL(meta.authorization_endpoint);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: provider.client_id,
    redirect_uri: redirectUri,
    scope: (provider.scopes?.length ? provider.scopes : ["openid"]).join(" "),
    state,
    code_challenge: challenge,
    code_challenge_method: "S256",
  });

  log(`open this URL to log in (listening on ${redirectUri}):\n\n  ${authorize}\n`);
  if (!noBrowser) openBrowser(authorize.toString());

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
    }),
  });
}

async function basicCredentials() {
  const rl = createInterface({ input: process.stdin, output: process.stderr });
  const user = await rl.question("username: ");
  const password = await rl.question("password: ");
  rl.close();
  return "Basic " + Buffer.from(`${user}:${password}`).toString("base64");
}

async function main() {
  const discovery = await getJson(`${clusterUrl}/.well-known/camunda-auth`);
  log("discovery:", JSON.stringify(discovery));

  let authorization;
  if (discovery.method === "oidc") {
    if (!discovery.providers?.length) throw new Error("cluster announces no CLI client for any provider");
    const provider = await choose(discovery.providers);
    log(`logging in via '${provider.id}' with public client '${provider.client_id}'`);
    const tokens = await oidcLogin(provider);
    const claims = decodeJwt(tokens.access_token) ?? {};
    log("access token:", JSON.stringify({ iss: claims.iss, aud: claims.aud, azp: claims.azp, sub: claims.sub, preferred_username: claims.preferred_username, exp: claims.exp && new Date(claims.exp * 1000).toISOString() }));
    log("refresh token:", tokens.refresh_token ? "received" : "none");
    authorization = `Bearer ${tokens.access_token}`;
  } else if (discovery.method === "basic") {
    authorization = await basicCredentials();
  }

  const res = await fetch(`${clusterUrl}${apiPath}`, { headers: authorization ? { authorization } : {} });
  log(`GET ${apiPath} -> ${res.status}`);
  console.log(await res.text());
  process.exit(res.ok ? 0 : 1);
}

main().catch((e) => {
  console.error("✖", e.message);
  process.exit(1);
});
