// Shared utility to derive a guaranteed non-matching string for a given regex pattern.
// Returns undefined if we cannot confidently craft a mismatch (pattern appears overly permissive).

export interface PatternMismatchOptions {
  // When true, avoid characters that would alter URL path segmentation or routing (e.g., '/', '\\', newline, '\\0').
  pathSegmentSafe?: boolean;
  // Additional disallowed characters caller wants excluded from generated mismatch.
  disallow?: string[];
}

// Shared utility to derive a guaranteed non-matching string for a given regex pattern.
// Returns undefined if we cannot confidently craft a mismatch (pattern appears overly permissive).
export function buildGuaranteedPatternMismatch(
  pattern: string,
  opts: PatternMismatchOptions = {},
): string | undefined {
  let rx: RegExp | undefined;
  try {
    rx = new RegExp(pattern);
  } catch {
    return undefined;
  }
  // Probe for permissive pattern (matches everything we throw at it)
  const probe = [
    'a',
    '1',
    '!',
    '_',
    '@',
    'abc123',
    'A.B',
    'x-y',
    '+plus',
    '',
    '\n',
  ];
  if (probe.every((s) => rx!.test(s))) return undefined;

  const candidates: string[] = [];
  // Digit-only (allowing optional leading '-')
  if (pattern === '^[0-9]+$' || pattern === '^-?[0-9]+$') candidates.push('a');
  // newline (often excluded by ^[...]+$ constructs)
  if (!opts.pathSegmentSafe) candidates.push('\\n');
  // Extract simple whitelist class ^[...]([+*])?$
  const cls = pattern.match(/^\^\[([^\]]+)]([+*])\$$/);
  if (cls) {
    const allowed = cls[1];
    const sentinels = ['!', ' ', '#', '%', '\\t'];
    for (const s of sentinels) if (!allowed.includes(s)) candidates.push(s);
    candidates.push('\\u2603');
  }
  // Add unusual/control/unicode & composite tokens
  candidates.push('!INVALID!');
  candidates.push('INVALID!');
  if (!opts.pathSegmentSafe) candidates.push('\\u0000');
  candidates.push(' '); // space (often excluded in tight whitelists)

  // For path segment safe mode, filter out candidates containing reserved or segment delimiters.
  const reservedForPath = new Set(['/', '\\']);
  function isAllowedForPath(candidate: string): boolean {
    if (!opts.pathSegmentSafe) return true;
    const materialized = candidate
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '\t')
      .replace(/\\u2603/g, '\u2603')
      .replace(/\\u0000/g, '\u0000');
    if ([...materialized].some((ch) => reservedForPath.has(ch))) return false;
    if (/\s/.test(materialized)) return false; // avoid whitespace which may be normalized by routers
    return true;
  }

  // Deduplicate and test
  const seen = new Set<string>();
  for (const raw of candidates) {
    if (seen.has(raw)) continue;
    seen.add(raw);
    if (!isAllowedForPath(raw)) continue;
    if (opts.disallow && opts.disallow.includes(raw)) continue;
    const materialized = raw
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '\t')
      .replace(/\\u2603/g, '\u2603')
      .replace(/\\u0000/g, '\u0000');
    if (!rx!.test(materialized)) return materialized;
  }
  return undefined;
}
