import type { ParsedRef, RefKind } from '../types';

/**
 * Pure, section-scoped reference parser. Shared verbatim with the generator
 * (#57713) — no IO, no repo awareness. Cross-repo detection and issue-vs-PR
 * classification belong to the Resolver, not here.
 */

/** The template's opt-out phrase. Kept as an exported constant so the PR template
 *  and the parser cannot drift (enforced by the repo-constant grep in CI). */
export const OPT_OUT_PHRASE = 'this pr does not need a linked issue';

/** The section whose refs the gate evaluates. */
export const SECTION_HEADING = 'Related issues';

// GitHub's closing keywords + our custom "completes". Case-insensitive.
const CLOSING = /^(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|completes?)$/i;
const RELATES = /^relates?\s+to$/i;
const BACKPORT = /^backport\s+of$/i;

// Optional keyword prefix shared by both ref shapes.
const KW = String.raw`(?:\b(close[sd]?|fix(?:e[sd])?|resolve[sd]?|completes?|relates?\s+to|backport\s+of)\b[\s:]+)?`;
const OWNER_REPO = String.raw`([A-Za-z0-9][\w.-]*\/[A-Za-z0-9][\w.-]*)`;

// "closes #12", "camunda/other#7", bare "#12".
const SHORTHAND = new RegExp(KW + `(?:${OWNER_REPO})?#(\\d+)`, 'gi');
// Full GitHub URLs: ".../owner/repo/issues/12" or ".../pull/12".
const URL = new RegExp(
  KW + String.raw`https?:\/\/github\.com\/${OWNER_REPO}\/(?:issues|pull)\/(\d+)`,
  'gi',
);

function kindOf(keyword: string | null): RefKind {
  if (keyword && BACKPORT.test(keyword)) return 'backport';
  if (keyword && RELATES.test(keyword)) return 'contributor';
  if (keyword && CLOSING.test(keyword)) return 'closing';
  return 'contributor'; // bare "#N"
}

/** Extract every reference from the given text (already scoped by the caller). */
export function parseRefs(text: string): ParsedRef[] {
  const refs: ParsedRef[] = [];
  const seen = new Set<number>(); // dedupe by match offset

  const push = (m: RegExpExecArray, repo: string | null, num: string) => {
    if (seen.has(m.index)) return;
    seen.add(m.index);
    const keyword = m[1] ? m[1].toLowerCase().replace(/\s+/g, ' ') : null;
    refs.push({
      raw: m[0].trim(),
      number: Number(num),
      repo: repo ?? null,
      keyword,
      kind: kindOf(keyword),
      index: m.index,
    });
  };

  for (const m of text.matchAll(URL)) push(m, m[2] ?? null, m[3]!);
  for (const m of text.matchAll(SHORTHAND)) push(m, m[2] ?? null, m[3]!);

  return refs.sort((a, b) => a.index - b.index);
}

/**
 * Slice out a markdown section body: everything after the matching heading up
 * to the next heading of any level (or EOF). Returns null if absent.
 */
export function extractSection(body: string, heading = SECTION_HEADING): string | null {
  const lines = body.split(/\r?\n/);
  const headingRe = new RegExp(`^#{1,6}\\s+${escapeRe(heading)}\\s*$`, 'i');
  const start = lines.findIndex((l) => headingRe.test(l.trim()));
  if (start < 0) return null;
  const rest = lines.slice(start + 1);
  const end = rest.findIndex((l) => /^#{1,6}\s+\S/.test(l));
  return (end < 0 ? rest : rest.slice(0, end)).join('\n');
}

/** True when the opt-out checkbox is present and ticked. */
export function isOptOutTicked(body: string): boolean {
  const re = new RegExp(String.raw`^\s*[-*]\s*\[x\]\s*.*${escapeRe(OPT_OUT_PHRASE)}`, 'im');
  return re.test(body);
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
