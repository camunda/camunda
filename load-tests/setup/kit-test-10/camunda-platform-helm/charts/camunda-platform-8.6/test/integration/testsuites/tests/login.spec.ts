// NOTE: If you get the message connection error: desc = "error reading server preface: http2: frame too large"
// this is likely due to --insecure on the zbctl call while the endpoint is TLS enabled.

/// <reference types="node" />

import { config as dotenv } from "dotenv";
dotenv(); // ← loads .env before anything else

import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "child_process";

// ---------- config & helpers ----------
function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required env var: ${name}`);
  return value;
}

// ANSI color helpers (kept minimal to avoid extra deps)
const C = {
  reset: "\x1b[0m",
  magenta: "\x1b[35m",
  dim: "\x1b[2m",
};

const verbose = /^true$/i.test(process.env.VERBOSE || "");
const vLog = (...args: unknown[]) => {
  if (verbose) {
    // eslint-disable-next-line no-console
    console.log(
      `${C.magenta}[playwright-verbose]${C.reset} ${C.dim}${new Date().toISOString()}${C.reset}`,
      ...args,
    );
  }
};

// Minimal shell escaping for single-quoted strings
function shEscapeSingle(str: string): string {
  return String(str).replace(/'/g, `'\\''`);
}

function buildCurl(
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH",
  url: string,
  headers: Record<string, string | undefined> = {},
  body?: string,
): string {
  const parts: string[] = ["curl", "-sS", "-X", method, `'${shEscapeSingle(url)}'`];
  for (const [k, v] of Object.entries(headers)) {
    if (!v) continue;
    parts.push("-H", `'${shEscapeSingle(`${k}: ${v}`)}'`);
  }
  if (body !== undefined && body !== "") {
    parts.push("--data-raw", `'${shEscapeSingle(body)}'`);
  }
  return parts.join(" ");
}

function buildZbctl(args: Array<string>): string {
  return ["zbctl", ...args.map((a) => `'${shEscapeSingle(a)}'`)].join(" ");
}

// Common zbctl helpers
function buildCommonZbctlArgs(redacted: boolean): string[] {
  return [
    "--clientCache",
    "/tmp/zeebe",
    "--clientId",
    config.venomID,
    "--clientSecret",
    redacted ? "<REDACTED_ADMIN_CLIENT_SECRET>" : config.venomSec,
    "--authzUrl",
    config.authURL,
    "--address",
    config.base.zeebeGRPC,
  ];
}

function runZbctl(commandArgs: string[], extraArgs: string[] = []) {
  vLog("Replay:", buildZbctl([...commandArgs, ...buildCommonZbctlArgs(true), ...extraArgs]));
  return execFileSync(
    "zbctl",
    [...commandArgs, ...buildCommonZbctlArgs(false), ...extraArgs],
    { stdio: "inherit" },
  );
}

// ---------- HTTP logging helpers ----------
function maskHeadersForCurl(headers: Record<string, string | undefined> = {}) {
  const masked: Record<string, string> = {};
  for (const [k, v] of Object.entries(headers)) {
    if (!v) continue;
    if (k.toLowerCase() === "authorization" && v.startsWith("Bearer ")) {
      masked[k] = "Bearer ${VENOM_JWT}";
    } else {
      masked[k] = v;
    }
  }
  return masked;
}

async function httpGet(
  api: APIRequestContext,
  url: string,
  headers: Record<string, string> = {},
  timeout = 45_000,
) {
  vLog("GET", url);
  vLog("Replay curl:", buildCurl("GET", url, maskHeadersForCurl(headers)));
  const r = await api.get(url, { headers, timeout });
  return r;
}

async function httpJson(
  api: APIRequestContext,
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH",
  url: string,
  body: string | object | undefined,
  headers: Record<string, string> = {},
  timeout = 45_000,
) {
  const allHeaders = { "Content-Type": "application/json", ...headers };
  const bodyString =
    typeof body === "string" ? body : body ? JSON.stringify(body) : undefined;
  vLog(method, url);
  vLog("Replay curl:", buildCurl(method, url, maskHeadersForCurl(allHeaders), bodyString));
  const r = await api.fetch(url, {
    method,
    data: body,
    headers: allHeaders,
    timeout,
  });
  return r;
}

// Grouped config for base URLs
const config = {
  authURL: requireEnv("AUTH_URL"),
  base: {
    console: requireEnv("CONSOLE_BASE_URL"),
    keycloak: requireEnv("KEYCLOAK_BASE_URL"),
    identity: requireEnv("IDENTITY_BASE_URL"),
    operate: requireEnv("OPERATE_BASE_URL"),
    optimize: requireEnv("OPTIMIZE_BASE_URL"),
    tasklist: requireEnv("TASKLIST_BASE_URL"),
    webModeler: requireEnv("WEBMODELER_BASE_URL"),
    connectors: requireEnv("CONNECTORS_BASE_URL"),
    zeebeGRPC: requireEnv("ZEEBE_GATEWAY_GRPC"),
    zeebeREST: requireEnv("ZEEBE_GATEWAY_REST"),
  },
  loginPath: {
    Console: requireEnv("CONSOLE_LOGIN_PATH"),
    Keycloak: requireEnv("KEYCLOAK_LOGIN_PATH"),
    Identity: process.env["IDENTITY_LOGIN_PATH"],
    Operate: requireEnv("OPERATE_LOGIN_PATH"),
    Optimize: requireEnv("OPTIMIZE_LOGIN_PATH"),
    Tasklist: requireEnv("TASKLIST_LOGIN_PATH"),
    WebModeler: requireEnv("WEBMODELER_LOGIN_PATH"),
    connectors: requireEnv("CONNECTORS_LOGIN_PATH"),
    zeebeGRPC: requireEnv("ZEEBE_GATEWAY_GRPC"),
    zeebeREST: requireEnv("ZEEBE_GATEWAY_REST"),
  },
  secrets: {
    connectors: requireEnv("PLAYWRIGHT_VAR_CONNECTORS_CLIENT_SECRET"),
    tasklist: requireEnv("PLAYWRIGHT_VAR_TASKLIST_CLIENT_SECRET"),
    operate: requireEnv("PLAYWRIGHT_VAR_OPERATE_CLIENT_SECRET"),
    optimize: requireEnv("PLAYWRIGHT_VAR_OPTIMIZE_CLIENT_SECRET"),
    zeebe: requireEnv("PLAYWRIGHT_VAR_ZEEBE_CLIENT_SECRET"),
  },
  venomID: process.env.TEST_CLIENT_ID ?? "venom",
  venomSec: requireEnv("PLAYWRIGHT_VAR_ADMIN_CLIENT_SECRET"),
  fixturesDir: process.env.FIXTURES_DIR || "/mnt/fixtures",
};

// Helper to fetch a token
async function fetchToken(id: string, sec: string, api: APIRequestContext) {
  vLog("Fetching token for client_id:", id);
  vLog(
    "Replay curl:",
    buildCurl(
      "POST",
      config.authURL,
      { "Content-Type": "application/x-www-form-urlencoded" },
      `client_id=${encodeURIComponent(id)}&client_secret=<REDACTED_${id.toUpperCase()}_SECRET>&grant_type=client_credentials`,
    ),
  );
  const r = await api.post(config.authURL, {
    form: {
      client_id: id,
      client_secret: sec,
      grant_type: "client_credentials",
    },
  });
  vLog("Token fetch status for", id, "->", r.status());
  expect(
    r.ok(),
    `Failed to get token for client_id=${id}: ${r.status()}`,
  ).toBeTruthy();
  return (await r.json()).access_token as string;
}

// ---------- tests ----------
test.describe("Camunda core", () => {
  let api: APIRequestContext;
  let venomJWT: string;

  test.beforeAll(async ({ playwright }) => {
    api = await playwright.request.newContext();
    vLog("Environment summary:", {
      authURL: config.authURL,
      base: config.base,
      loginPath: {
        Console: config.loginPath.Console,
        Keycloak: config.loginPath.Keycloak,
        Identity: config.loginPath.Identity,
        Operate: config.loginPath.Operate,
        Optimize: config.loginPath.Optimize,
        Tasklist: config.loginPath.Tasklist,
        WebModeler: config.loginPath.WebModeler,
        connectors: config.loginPath.connectors,
      },
      venomID: config.venomID,
      fixturesDir: config.fixturesDir,
    });
    venomJWT = await fetchToken(config.venomID, config.venomSec, api);
    vLog("Fetched admin token (redacted) for venomID:", config.venomID);
  });

  test("M2M tokens", async () => {
    vLog("Starting M2M tokens test");
    for (const [id, sec] of Object.entries(config.secrets)) {
      // ensure each call resolves and yields a non-empty JWT:
      await expect(fetchToken(id, sec, api)).resolves.toMatch(
        /^[\w-]+\.[\w-]+\.[\w-]+$/,
      );
    }
    vLog("Completed M2M tokens test");
  });

  // Parameterized login page tests
  for (const [name, url] of Object.entries({
    Console: config.base.console,
    Keycloak: config.base.keycloak,
    Identity: config.base.identity,
    Operate: config.base.operate,
    Optimize: config.base.optimize,
    Tasklist: config.base.tasklist,
    WebModeler: config.base.webModeler,
  })) {
    test(`Login page: ${name}`, async () => {
      vLog(`GET login page: ${name}`, `${url}${config.loginPath[name]}`);
      const r = await httpGet(api, `${url}${config.loginPath[name]}`);
      vLog(`Login page response: ${name}`, r.status());
      expect(
        r.ok(),
        `Login page failed for ${name}: ${r.status()}`,
      ).toBeTruthy();
      expect(
        await r.text(),
        `Login page for ${name} contains error`,
      ).not.toMatch(/error/i);
    });
  }
  test("Connectors inbound page", async () => {
    vLog("GET connectors inbound:", config.base.connectors);
    expect((await httpGet(api, config.base.connectors)).ok(), "Connectors inbound page failed").toBeTruthy();
  });

  // Parameterized API endpoint tests
  for (const [label, url, method, body] of [
    ["Console clusters", `${config.base.console}/api/clusters`, "GET", ""],
    ["Identity users", `${config.base.identity}api/users`, "GET", ""],
    [
      "Operate defs",
      `${config.base.operate}/v1/process-definitions/search`,
      "POST",
      "{}",
    ],
    [
      "Tasklist tasks",
      `${config.base.tasklist}/graphql`,
      "POST",
      '{"query":"{tasks(query:{}){id name}}"}',
    ],
  ] as const) {
    test(`API: ${label}`, async () => {
      vLog("API call:", { label, url, method });
      const r = await httpJson(
        api,
        method,
        url,
        body || undefined,
        { Authorization: `Bearer ${venomJWT}` },
      );
      vLog("API response:", { label, status: r.status() });
      expect(
        r.ok(),
        `API call failed for ${label}: ${r.status()}`,
      ).toBeTruthy();
    });
  }

  test("WebModeler login page", async () => {
    vLog("GET WebModeler:", config.base.webModeler);
    const r = await httpGet(api, config.base.webModeler);
    vLog("WebModeler response status:", r.status());
    expect(r.ok(), "WebModeler login page failed").toBeTruthy();
    expect(await r.text(), "WebModeler login page contains error").not.toMatch(
      /error/i,
    );
  });

  test("Zeebe status (gRPC)", async () => {
    const extra =
      process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
    vLog("zbctl status:", { extra });
    const out = execFileSync("zbctl", ["status", ...buildCommonZbctlArgs(false), ...extra], {
      encoding: "utf-8",
    });
    vLog("Replay:", buildZbctl(["status", ...buildCommonZbctlArgs(true), ...extra]));
    expect(out, "zbctl status output missing Leader, Healthy").toMatch(
      /Leader, Healthy/,
    );
    expect(out, "zbctl status output contains Unhealthy").not.toMatch(
      /Unhealthy/,
    );
  });
  //
  //  test("Zeebe topology (REST)", async () => {
  //    const r = await api.get(`${config.base.zeebeREST}/v1/topology`, {
  //      headers: { Authorization: `Bearer ${venomJWT}` },
  //    });
  //    expect(r.ok(), "Zeebe topology REST call failed").toBeTruthy();
  //    expect(
  //      await r.json(),
  //      "Zeebe topology response missing brokers",
  //    ).toHaveProperty("brokers");
  //  });
  //
  // Parameterized BPMN deploy tests
  for (const [name, file] of [
    ["Basic", "test-process.bpmn"],
    ["Inbound", "test-inbound-process.bpmn"],
  ] as const) {
    const extra =
      process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
    test(`Deploy BPMN: ${name}`, async () => {
      vLog("zbctl deploy:", { name, file, extra });
      runZbctl(["deploy", `${config.fixturesDir}/${file}`], extra);
    });
  }

  // Parameterized process visibility tests

  for (const [bpmnId, label, file] of [
    ["it-test-process", "Basic", "test-process.bpmn"],
    ["test-inbound-process", "Inbound", "test-inbound-process.bpmn"],
  ] as const) {
    test(`Process visible: ${label}`, async () => {
      test.setTimeout(3 * 60 * 1000); // > polling window

      const extra =
        process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
      vLog("zbctl deploy (visibility pre-step):", { label, file, extra });
      runZbctl(["deploy", `${config.fixturesDir}/${file}`], extra);

      const timeoutMs = 2 * 60 * 1000;
      const intervalMs = 5 * 1000;
      const start = Date.now();

      let found = false;
      let lastStatus = 0;

      /* eslint-disable no-await-in-loop */
      while (Date.now() - start < timeoutMs) {
        vLog("Polling Operate for process definitions…");
        const r = await httpJson(
          api,
          "POST",
          `${config.base.operate}/v1/process-definitions/search`,
          {},
          { Authorization: `Bearer ${venomJWT}` },
        );

        lastStatus = r.status();

        if (r.ok()) {
          const data = await r.json();
          vLog("Operate search status:", lastStatus, "items count:", (data.items as unknown[] | undefined)?.length ?? 0);
          const ids = (data.items as Array<{ bpmnProcessId: string }> | []).map(
            (i) => i.bpmnProcessId,
          );

          if (ids.includes(bpmnId)) {
            vLog("Process found:", bpmnId);
            found = true;
            break; // success
          }
        }

        await new Promise((res) => setTimeout(res, intervalMs));
      }

      expect(
        found,
        `Process ${bpmnId} not visible in Operate within ${timeoutMs / 1000}s (last status ${lastStatus})`,
      ).toBeTruthy();
    });
  }
});


