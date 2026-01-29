/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Attempts to repair nearly-JSON strings into valid JSON.
 *
 * Why:
 * - Some agentContext providers serialize JSON in a non-strict way (e.g. unescaped newlines
 *   or quotes inside string fields), which makes `JSON.parse` fail.
 *
 * Strategy:
 * - First, try the input as-is.
 * - If it fails, fall back to a minimal repair approach:
 *   - Replace unescaped newlines inside quoted strings with `\\n`.
 *
 * This is intentionally conservative (we don't try to accept comments/trailing commas/etc.).
 */
function tryRepairJsonString(input: string): string {
  // Fast path: nothing to do
  if (!input.includes('\n') && !input.includes('\r')) {
    return input;
  }

  // Walk the string and escape newlines that occur while inside a JSON string.
  // This handles payloads where actual newline characters end up inside a quoted string.
  let inString = false;
  let escaped = false;
  let out = '';

  for (let i = 0; i < input.length; i++) {
    const ch = input[i];

    if (escaped) {
      out += ch;
      escaped = false;
      continue;
    }

    if (ch === '\\') {
      out += ch;
      escaped = true;
      continue;
    }

    if (ch === '"') {
      out += ch;
      inString = !inString;
      continue;
    }

    if (inString && (ch === '\n' || ch === '\r')) {
      out += ch === '\n' ? '\\n' : '\\r';
      continue;
    }

    out += ch;
  }

  return out;
}

function safeParseJsonWithRepair(
  value: string,
):
  | {parsed: unknown; parseError: null; repaired: boolean}
  | {parsed: null; parseError: Error; repaired: boolean} {
  try {
    const parsed = JSON.parse(value);

    // If the value itself is a JSON-encoded string containing JSON, try to parse it.
    // This happens when the variable is stored as a stringified JSON blob.
    if (typeof parsed === 'string') {
      const inner = parsed;
      const repairedInner = tryRepairJsonString(inner);
      try {
        return {
          parsed: JSON.parse(repairedInner),
          parseError: null,
          repaired: repairedInner !== inner,
        };
      } catch (_innerErr) {
        // Fall back to returning the outer parsed string if inner can't be parsed.
        return {parsed, parseError: null, repaired: false};
      }
    }

    return {parsed, parseError: null, repaired: false};
  } catch (e1) {
    const repairedValue = tryRepairJsonString(value);
    if (repairedValue !== value) {
      try {
        const parsed = JSON.parse(repairedValue);

        if (typeof parsed === 'string') {
          const repairedInner = tryRepairJsonString(parsed);
          try {
            return {
              parsed: JSON.parse(repairedInner),
              parseError: null,
              repaired: true,
            };
          } catch {
            return {parsed, parseError: null, repaired: true};
          }
        }

        return {
          parsed,
          parseError: null,
          repaired: true,
        };
      } catch (e2) {
        const error = e2 instanceof Error ? e2 : new Error(String(e2));
        return {parsed: null, parseError: error, repaired: true};
      }
    }

    const error = e1 instanceof Error ? e1 : new Error(String(e1));
    return {parsed: null, parseError: error, repaired: false};
  }
}

export {safeParseJsonWithRepair};
