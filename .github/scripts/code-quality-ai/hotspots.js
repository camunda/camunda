#!/usr/bin/env node
/**
 * Hotspot identification using code-maat.
 *
 * Generates a `git log` over a configurable window, feeds it to
 * code-maat's `revisions` analysis (churn per file), then ranks files
 * by `revisions × lines-of-code` — the canonical hotspot heuristic
 * from Adam Tornhill's "Your Code as a Crime Scene".
 *
 * The code-maat JAR is provisioned by the caller. In CI the workflow
 * downloads it via `robinraju/release-downloader` and exports its path
 * as `CODE_MAAT_JAR`. For local runs, pass `--jar <path>` or set the
 * same env var.
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const MAX_BUFFER = 256 * 1024 * 1024;

function resolveJarPath(explicit) {
  const jar = explicit ?? process.env.CODE_MAAT_JAR;
  if (!jar) {
    throw new Error(
      "code-maat JAR not provided. Set CODE_MAAT_JAR or pass --jar <path>.",
    );
  }
  if (!fs.existsSync(jar)) {
    throw new Error(`code-maat JAR not found at ${jar}.`);
  }
  return jar;
}

function generateGitLog(repoRoot, sinceDate) {
  const args = [
    "log",
    "--no-merges",
    "--numstat",
    "--date=short",
    "--pretty=format:--%h--%ad--%aN",
  ];
  if (sinceDate) args.push(`--since=${sinceDate}`);
  const out = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: MAX_BUFFER,
  });
  if (out.status !== 0) {
    throw new Error(`git log failed: ${out.stderr.trim()}`);
  }
  return out.stdout;
}

function runCodeMaat(jarPath, logContent) {
  const tmpLog = path.join(
    os.tmpdir(),
    `code-maat-log-${process.pid}-${Date.now()}.log`,
  );
  fs.writeFileSync(tmpLog, logContent);
  try {
    const result = spawnSync(
      "java",
      ["-jar", jarPath, "-l", tmpLog, "-c", "git2", "-a", "revisions"],
      { encoding: "utf8", maxBuffer: MAX_BUFFER },
    );
    if (result.status !== 0) {
      throw new Error(`code-maat failed: ${result.stderr.trim()}`);
    }
    return result.stdout;
  } finally {
    fs.unlinkSync(tmpLog);
  }
}

function parseRevisionsCsv(csv) {
  const lines = csv.trim().split("\n");
  const out = [];
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line) continue;
    const comma = line.lastIndexOf(",");
    const entity = line.slice(0, comma);
    const revisions = parseInt(line.slice(comma + 1), 10);
    if (Number.isFinite(revisions)) out.push({ entity, revisions });
  }
  return out;
}

function countLines(filePath) {
  try {
    return fs.readFileSync(filePath, "utf8").split("\n").length;
  } catch {
    return 0;
  }
}

/**
 * @typedef {Object} Hotspot
 * @property {string} entity   File path relative to repo root.
 * @property {number} revisions Number of commits touching the file.
 * @property {number} loc      Lines of code (raw line count).
 * @property {number} score    Hotspot score: revisions × loc.
 */

/**
 * Identify hotspots in a Git repository.
 *
 * @param {Object} [opts]
 * @param {string|null} [opts.jarPath]    Path to code-maat JAR. Falls back to CODE_MAAT_JAR env var.
 * @param {number|null} [opts.topN]       Limit results to top N. null = all.
 * @param {string|null} [opts.sinceDate]  Git --since value, e.g. "90 days ago".
 * @param {string} [opts.repoRoot]        Repo working tree, defaults to cwd.
 * @param {string[]} [opts.extensions]    File extensions to include.
 * @param {RegExp|null} [opts.pathPrefix] Restrict to paths matching this regex.
 * @returns {Promise<Hotspot[]>}
 */
export async function getHotspots({
  jarPath = null,
  topN = null,
  sinceDate = "90 days ago",
  repoRoot = process.cwd(),
  extensions = [".java"],
  pathPrefix = null,
} = {}) {
  const jar = resolveJarPath(jarPath);
  const log = generateGitLog(repoRoot, sinceDate);
  const csv = runCodeMaat(jar, log);
  const revs = parseRevisionsCsv(csv);

  const scored = [];
  for (const { entity, revisions } of revs) {
    if (!extensions.some((ext) => entity.endsWith(ext))) continue;
    if (pathPrefix && !pathPrefix.test(entity)) continue;
    const loc = countLines(path.join(repoRoot, entity));
    if (loc === 0) continue;
    scored.push({ entity, revisions, loc, score: revisions * loc });
  }
  scored.sort((a, b) => b.score - a.score);
  return topN === null || topN === undefined
    ? scored
    : scored.slice(0, topN);
}

function parseArgs(argv) {
  const args = {
    jar: null,
    top: null,
    since: "90 days ago",
    extensions: [".java"],
    pathPrefix: null,
    format: "lines",
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--jar":
        args.jar = argv[++i];
        break;
      case "--top":
        args.top = parseInt(argv[++i], 10);
        break;
      case "--since":
        args.since = argv[++i];
        break;
      case "--extensions":
        args.extensions = argv[++i].split(",");
        break;
      case "--path-prefix":
        args.pathPrefix = new RegExp(argv[++i]);
        break;
      case "--format":
        args.format = argv[++i];
        break;
      case "-h":
      case "--help":
        console.log(
          [
            "Usage: hotspots.js [options]",
            "  --jar PATH          code-maat JAR (or set CODE_MAAT_JAR)",
            "  --top N             keep top N hotspots",
            "  --since S           git --since value (default: '90 days ago')",
            "  --extensions LIST   comma-separated list (default: .java)",
            "  --path-prefix RE    regex restricting paths (e.g. '^zeebe/')",
            "  --format F          lines | json | tsv (default: lines)",
          ].join("\n"),
        );
        process.exit(0);
        break;
      default:
        console.error(`Unknown argument: ${a}`);
        process.exit(2);
    }
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const hotspots = await getHotspots({
    jarPath: args.jar,
    topN: args.top,
    sinceDate: args.since,
    extensions: args.extensions,
    pathPrefix: args.pathPrefix,
  });
  switch (args.format) {
    case "json":
      process.stdout.write(JSON.stringify(hotspots, null, 2) + "\n");
      break;
    case "tsv":
      process.stdout.write("score\trevisions\tloc\tentity\n");
      for (const h of hotspots) {
        process.stdout.write(
          `${h.score}\t${h.revisions}\t${h.loc}\t${h.entity}\n`,
        );
      }
      break;
    default:
      for (const h of hotspots) console.log(h.entity);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err.message);
    process.exit(1);
  });
}
