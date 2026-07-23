import type { TitleDecision } from '../types';

/**
 * PR-title lint — the active rules of `commitlint.config.cjs`, reimplemented as
 * a pure check so the action keeps zero runtime deps (pulling @commitlint +
 * config-conventional would vendor hundreds of kB into the committed bundle for
 * a handful of trivial rules). The config's other rules are disabled ([0,...]).
 *
 * DRIFT GUARD: TITLE_TYPES and HEADER_MAX are the single source of truth here,
 * and the action CI greps commitlint.config.cjs to assert they still match —
 * so a change to the repo's commit rules fails CI until this is updated.
 *
 * Active rules mirrored (see commitlint.config.cjs):
 *   type-empty:never · type-case:lower-case · type-enum · scope-empty:always ·
 *   header-max-length:120. Subject/body/footer rules are disabled there.
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
      reasons: [`Scopes are not used in this repo — drop "${scope}" and write "${type}: …".`],
    };
  }

  if (type !== type?.toLowerCase()) {
    return { outcome: 'fail', code: 'title-type', reasons: [`The type "${type}" must be lower-case.`] };
  }

  if (!TITLE_TYPES.includes(type as (typeof TITLE_TYPES)[number])) {
    return {
      outcome: 'fail',
      code: 'title-type',
      reasons: [`"${type}" is not an allowed type. Use one of: ${TITLE_TYPES.join(', ')}.`],
    };
  }

  return { outcome: 'pass', code: 'title-ok', reasons: [`Title type "${type}" is valid.`] };
}

/**
 * Bot authors whose titles are machine-generated and exempt from title lint
 * (D16). Their PR-issue link / backport marker is still validated — only the
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
