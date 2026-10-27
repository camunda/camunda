import type { TitleDecision } from '../types';

/**
 * PR-title lint — `commitlint.config.cjs`'s active rules (type-empty,
 * type-case, type-enum, scope-empty, header-max-length), reimplemented pure
 * to keep the action's runtime deps at zero. CI greps that config to assert
 * TITLE_TYPES/HEADER_MAX still match, so drift fails CI, not a release.
 */

/** commitlint.config.cjs `type-enum`. Keep in sync — CI enforces it. */
export const TITLE_TYPES = [
  'build',
  'ci',
  'deps',
  'docs',
  'feat',
  'fix',
  'merge',
  'perf',
  'refactor',
  'revert',
  'style',
  'test',
] as const;

/** commitlint.config.cjs `header-max-length`. Keep in sync — CI enforces it. */
export const HEADER_MAX = 120;

// `type` + optional `(scope)` + optional `!` + `: ` + subject. Mirrors the
// conventional-commit header shape config-conventional parses.
const HEADER = /^(?<type>[^\s():!]+)(?<scope>\([^)]*\))?!?:[ ](?<subject>.+)$/;

/** Wraps a title fragment before it goes into the sticky comment — the gate
 *  posts with a write token, so a raw `@mention` would notify via the bot. */
function code(value: string | undefined): string {
  return `\`${(value ?? '').replace(/`/g, '')}\``;
}

/** Lint a PR title. Pure — no IO, no bot logic (the caller decides bot skips). */
export function lintTitle(title: string): TitleDecision {
  if (title.length > HEADER_MAX) {
    return {
      outcome: 'fail',
      code: 'title-length',
      reasons: [`The title is ${title.length} characters; keep it within ${HEADER_MAX}.`],
    };
  }

  const match = HEADER.exec(title);
  if (!match?.groups) {
    return {
      outcome: 'fail',
      code: 'title-format',
      reasons: [
        'The title must follow Conventional Commits: `type: summary` (e.g. "fix: correct retry backoff").',
        `Allowed types: ${TITLE_TYPES.join(', ')}.`,
      ],
    };
  }

  const { type, scope } = match.groups;

  if (scope) {
    return {
      outcome: 'fail',
      code: 'title-scope',
      reasons: [`Scopes are not used in this repo — drop ${code(scope)} and write "${code(type)}: …".`],
    };
  }

  if (type !== type?.toLowerCase()) {
    return { outcome: 'fail', code: 'title-type', reasons: [`The type ${code(type)} must be lower-case.`] };
  }

  if (!TITLE_TYPES.includes(type as (typeof TITLE_TYPES)[number])) {
    return {
      outcome: 'fail',
      code: 'title-type',
      reasons: [`${code(type)} is not an allowed type. Use one of: ${TITLE_TYPES.join(', ')}.`],
    };
  }

  return { outcome: 'pass', code: 'title-ok', reasons: [`Title type "${type}" is valid.`] };
}

/**
 * Bot authors whose titles are machine-generated and exempt from title lint.
 * Their PR-issue link / backport marker is still validated — only the
 * title check is skipped.
 */
export const BOT_TITLE_EXEMPT = new Set([
  'backport-action',
  'monorepo-devops-automation[bot]',
  'renovate[bot]',
  'dependabot[bot]',
]);

export function isTitleExemptAuthor(login: string | undefined): boolean {
  return login !== undefined && BOT_TITLE_EXEMPT.has(login);
}

/**
 * Bot authors exempt from the PR-issue-LINK check — they open PRs from their
 * own template and never tick the opt-out box.
 *
 * MUST STAY SEPARATE from BOT_TITLE_EXEMPT: that set includes
 * `monorepo-devops-automation[bot]`, the backport-PR author. Exempting it
 * here would skip the backport hop, silently dropping backports from notes.
 */
export const BOT_LINK_EXEMPT = new Set(['renovate[bot]']);

export function isLinkExemptAuthor(login: string | undefined): boolean {
  return login !== undefined && BOT_LINK_EXEMPT.has(login);
}
