#!/usr/bin/env node
//
// The daily Slack board of the OC flakiness fix agent
// (.github/workflows/c8-orchestration-cluster-flakiness-fix.yml).
//
// The pick job posts one message listing every test it dispatched, and each
// dispatched run rewrites that same message when it reaches a verdict, so the
// channel carries one live row per test instead of a burst of final reports
// hours apart.
//
//   node board.mjs --init targets.json --date 2026-11-12 --run-url <url>
//     -> board JSON on stdout, every row "dispatched"
//
//   node board.mjs --set board.json --fp ab12cd34 --status open_pr
//                  [--detail "..."] [--pr <url>] [--run <url>] [--rate 100]
//     -> the same board with that row replaced
//
//   node board.mjs --render board.json [--now 2026-11-12T09:21:00Z]
//     -> the Slack mrkdwn text
//
// A --set for a fingerprint the board does not carry is a no-op rather than an
// error: a manually dispatched run names a board it was never listed on, and
// failing there would turn a successful fix into a red run.

import {parseArgs, readJson} from './report.mjs';
import {pathToFileURL} from 'node:url';

export const CHANNEL = 'c8-orchestration-cluster-e2e-test-results';

const SCOPE =
  'Scope: the flakiest test(s) of the last OC E2E / API-ES nightlies, every version';

// How each verdict reads in the channel. `resolved` is false only for a run
// still in flight, so the headline counts what is still outstanding.
const STATUS = {
  dispatched: {icon: ':hourglass:', label: 'still running', resolved: false},
  open_pr: {icon: ':white_check_mark:', label: 'fixed', resolved: true},
  delete_branch: {icon: ':x:', label: 'closed — no improvement', resolved: true},
  not_reproduced: {
    icon: ':seedling:',
    label: 'the flake did not reproduce — nothing to fix',
    resolved: true,
  },
  blocked: {icon: ':wrench:', label: 'never reached a verdict', resolved: true},
  skipped: {
    icon: ':no_entry_sign:',
    label: 'skipped — a fix branch already covers this test',
    resolved: true,
  },
  infra: {icon: ':wrench:', label: 'the run failed before any verdict', resolved: true},
};

// The endings in which nobody measured the test: the stack never came up, the
// agent job died, or the run was lost. They need a person to repair the
// harness, and another fix agent tomorrow would hit the same wall.
const NEEDS_A_PERSON = ['blocked', 'infra'];

const statusOf = (row) => STATUS[row.status] ?? STATUS.dispatched;

// Slack renders a bare < or & inside mrkdwn as markup, which silently eats
// part of a test title (`a <b> c` loses `<b>`).
export function escape(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

const link = (url, text) => (url ? `<${url}|${escape(text)}>` : escape(text));

export function initBoard({targets, date, runUrl = '', channel = CHANNEL}) {
  return {
    date,
    channel,
    ts: '',
    run_url: runUrl,
    rows: targets.map((t) => ({
      fp: t.fp,
      version: t.version,
      file: t.file,
      title: (t.titlePath ?? []).join(' › '),
      cells: t.cells ?? [],
      status: 'dispatched',
      detail: '',
      pr: '',
      run: '',
    })),
  };
}

export function setRow(board, {fp, status, detail = '', pr = '', run = '', rate}) {
  if (!STATUS[status]) {
    throw new Error(`board.mjs: unknown status "${status}"`);
  }
  return {
    ...board,
    rows: (board.rows ?? []).map((row) =>
      row.fp === fp
        ? {
            ...row,
            status,
            detail,
            pr,
            run,
            ...(rate === undefined || rate === null || rate === '' ? {} : {rate: Number(rate)}),
          }
        : row,
    ),
  };
}

// The one line a row contributes to the "Run details" list.
function renderRow(row) {
  const {icon} = statusOf(row);
  const where = row.cells?.length > 0 ? ` (${escape(row.cells.join(', '))})` : '';
  const head = `  ${icon} \`${escape(row.file)}\`${row.title ? ` › ${escape(row.title)}` : ''}${where}`;
  const outcome = outcomeText(row);
  return outcome ? `${head} — ${outcome}` : head;
}

function outcomeText(row) {
  const {label} = statusOf(row);
  if (row.status === 'open_pr') {
    // The 100% case is what the channel is scanning for: a test that is
    // actually fixed, not merely improved past the threshold.
    const rate =
      row.rate === 100
        ? 'fixed — 100% pass rate'
        : row.rate >= 0
          ? `fixed — ${row.rate}% pass rate`
          : 'fixed';
    const prNumber = String(row.pr ?? '').match(/\/pull\/(\d+)/)?.[1];
    return link(row.pr, prNumber ? `PR #${prNumber} ${rate}` : rate);
  }
  if (row.status === 'dispatched') {
    return row.run ? link(row.run, label) : label;
  }
  const text = row.detail ? `${label} — ${row.detail}` : label;
  return row.run ? link(row.run, text) : escape(text);
}

export function render(board, {now = new Date()} = {}) {
  const rows = board.rows ?? [];
  const stamp = new Date(now).toISOString().slice(11, 16);
  const lines = [
    `:robot_face: *OC Flakiness Fix Agent — ${escape(board.date)}* _(updated ${stamp} UTC)_`,
    SCOPE,
  ];

  if (rows.length === 0) {
    lines.push(
      ':seedling: *No test met the bar today* — every candidate was already covered by a fix branch or is still cooling down.',
    );
    if (board.run_url) {
      lines.push('', `<${board.run_url}|Pick run>`);
    }
    return lines.join('\n');
  }

  const count = (pred) => rows.filter(pred).length;
  const resolved = count((r) => statusOf(r).resolved);
  const parts = [];
  const fixed = count((r) => r.status === 'open_pr');
  const perfect = count((r) => r.status === 'open_pr' && r.rate === 100);
  if (fixed > 0) {
    parts.push(
      `:white_check_mark: ${fixed} fixed${perfect > 0 ? ` (${perfect} at 100%)` : ''}`,
    );
  }
  const closed = count((r) => r.status === 'delete_branch');
  if (closed > 0) parts.push(`:x: ${closed} closed (no improvement)`);
  const green = count((r) => r.status === 'not_reproduced');
  if (green > 0) parts.push(`:seedling: ${green} did not reproduce`);
  const skipped = count((r) => r.status === 'skipped');
  if (skipped > 0) parts.push(`:no_entry_sign: ${skipped} skipped`);
  const stuck = count((r) => NEEDS_A_PERSON.includes(r.status));
  if (stuck > 0) parts.push(`:wrench: ${stuck} never reached a verdict`);
  const running = count((r) => r.status === 'dispatched');
  if (running > 0) parts.push(`:hourglass: ${running} still running`);

  let health = `:bar_chart: *${resolved}/${rows.length} run(s) resolved*`;
  if (parts.length > 0) health += ` — ${parts.join('  |  ')}`;
  lines.push(health, '', '*Run details:*');

  for (const version of [...new Set(rows.map((r) => r.version))]) {
    const forVersion = rows.filter((r) => r.version === version);
    lines.push(`*${escape(version)}* — ${forVersion.length} test(s)`);
    lines.push(...forVersion.map(renderRow));
  }

  const blocked = rows.filter((r) => NEEDS_A_PERSON.includes(r.status));
  if (blocked.length > 0) {
    lines.push(
      '',
      `:rotating_light: *${blocked.length} target(s) never reached a verdict — this needs a person, not another fix agent*`,
    );
    lines.push(
      ...blocked.map(
        (r) =>
          `• ${escape(r.version)} \`${escape(r.file)}\` — ${escape(r.detail || statusOf(r).label)}` +
          (r.run ? ` (<${r.run}|run>)` : ''),
      ),
    );
  }

  if (board.run_url) {
    lines.push('', `<${board.run_url}|Pick run>`);
  }
  return lines.join('\n');
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = parseArgs(process.argv.slice(2));
  if (args.init !== undefined) {
    const targets = readJson(args.init, []);
    process.stdout.write(
      `${JSON.stringify(
        initBoard({
          targets,
          date: args.date,
          runUrl: args['run-url'] ?? '',
          channel: args.channel || CHANNEL,
        }),
      )}\n`,
    );
  } else if (args.set !== undefined) {
    const board = readJson(args.set, null);
    if (!board) {
      console.error(`board.mjs: cannot read ${args.set}`);
      process.exit(2);
    }
    process.stdout.write(
      `${JSON.stringify(
        setRow(board, {
          fp: args.fp,
          status: args.status,
          detail: args.detail ?? '',
          pr: args.pr ?? '',
          run: args.run ?? '',
          rate: args.rate,
        }),
      )}\n`,
    );
  } else if (args.render !== undefined) {
    const board = readJson(args.render, null);
    if (!board) {
      console.error(`board.mjs: cannot read ${args.render}`);
      process.exit(2);
    }
    process.stdout.write(render(board, {now: args.now ? new Date(args.now) : new Date()}));
  } else {
    console.error('board.mjs: one of --init, --set or --render is required');
    process.exit(2);
  }
}
