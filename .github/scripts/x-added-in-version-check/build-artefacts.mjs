#!/usr/bin/env node
/**
 * Runs `build:endpoint-map` and `build:version-map` in parallel.
 *
 * The two underlying scripts touch independent temp dirs, independent
 * output paths (ENDPOINT_MAP_PATH vs VERSION_MAP_PATH), and independent
 * upstream repos, so they can race safely. Running them concurrently
 * roughly halves wall time, since each spends most of its budget in
 * network I/O (git clone) and `npm install` on the cloned tool.
 *
 * Output from each child is prefixed so interleaved lines stay readable.
 * Exits non-zero if either child fails.
 */
import { spawn } from "node:child_process";

const npm = process.platform === "win32" ? "npm.cmd" : "npm";

function run(label, script) {
  return new Promise((resolveP, rejectP) => {
    const child = spawn(npm, ["run", "--silent", script], {
      stdio: ["ignore", "pipe", "pipe"],
      env: process.env,
    });

    const prefix = (stream, line) => stream.write(`[${label}] ${line}\n`);
    const pipeLines = (src, dst) => {
      let buf = "";
      src.setEncoding("utf-8");
      src.on("data", (chunk) => {
        buf += chunk;
        let idx;
        while ((idx = buf.indexOf("\n")) >= 0) {
          prefix(dst, buf.slice(0, idx));
          buf = buf.slice(idx + 1);
        }
      });
      src.on("end", () => {
        if (buf.length) prefix(dst, buf);
      });
    };
    pipeLines(child.stdout, process.stdout);
    pipeLines(child.stderr, process.stderr);

    child.on("error", rejectP);
    child.on("close", (code) => {
      if (code === 0) resolveP();
      else rejectP(new Error(`${script} exited with code ${code}`));
    });
  });
}

const started = Date.now();
const results = await Promise.allSettled([
  run("endpoint-map", "build:endpoint-map"),
  run("version-map", "build:version-map"),
]);
const elapsed = ((Date.now() - started) / 1000).toFixed(1);

const failures = results
  .map((r, i) => (r.status === "rejected" ? r.reason.message : null))
  .filter(Boolean);

if (failures.length) {
  console.error(`\nbuild:artefacts failed after ${elapsed}s:`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}

console.log(`\nbuild:artefacts completed in ${elapsed}s`);
