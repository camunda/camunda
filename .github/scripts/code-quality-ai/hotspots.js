#!/usr/bin/env node
/**
 * Hotspot identification for the code quality AI pipeline.
 *
 * For the hackday MVP this returns a hardcoded list. The interface
 * mirrors what a real code-maat integration would expose so the
 * implementation can be swapped in later without changing callers.
 */

// Replace with code-maat output once Stage 1 integration lands.
const HACKDAY_HOTSPOTS = ["zeebe/engine"];

/**
 * Return hotspot paths (modules or files) in priority order.
 *
 * @param {number|null} [topN=null] Limit to first N entries.
 * @returns {string[]}
 */
export function getHotspots(topN = null) {
  if (topN === null || topN === undefined) {
    return [...HACKDAY_HOTSPOTS];
  }
  return HACKDAY_HOTSPOTS.slice(0, topN);
}

function parseArgs(argv) {
  const args = { top: null, format: "lines" };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--top") {
      args.top = parseInt(argv[++i], 10);
    } else if (a === "--format") {
      args.format = argv[++i];
    } else if (a === "-h" || a === "--help") {
      console.log("Usage: hotspots.js [--top N] [--format lines|json]");
      process.exit(0);
    } else {
      console.error(`Unknown argument: ${a}`);
      process.exit(2);
    }
  }
  return args;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const hotspots = getHotspots(args.top);
  if (args.format === "json") {
    process.stdout.write(JSON.stringify(hotspots) + "\n");
  } else {
    for (const h of hotspots) {
      console.log(h);
    }
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
