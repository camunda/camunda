// AlwaysGreen developer feedback — render one standard failure message and post
// it to the job summary, the PR (upserted), and Slack. Dependency-free.
//
// Inputs via env (set by action.yml):
//   FB_STAGE, FB_CATEGORY, FB_OWNER, FB_RUN_URL, FB_STATUS  (the classification contract)
//   FB_EVIDENCE        optional free-text evidence (e.g. failing step name)
//   FB_REPO            owner/repo of the failing run (for links)
//   FB_PR              PR number, or a merge_group head_ref to extract it from, or empty
//   FB_COOKBOOK_URL    debugging cookbook link
//   FB_SLACK_CHANNEL   Slack channel id/name (optional → skip Slack)
//   GH_TOKEN           token with PR write on FB_REPO (optional → skip PR comment)
//   SLACK_BOT_TOKEN    Slack bot token (optional → skip Slack)

import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {appendFile} from 'node:fs/promises';

const exec = promisify(execFile);
const env = (k, d = '') => (process.env[k] ?? d).trim();

const MARKER = '<!-- alwaysgreen-feedback -->'; // upsert anchor
const ASK = 'https://camunda.slack.com/archives/C0AQ378VBEV'; // #ask-alwaysgreen
const RELIABILITY =
  'https://camunda.slack.com/archives/C0AK0AVKRL0'; // #alwaysgreen-reliability

// Owner→medic Slack subteam map — keep in sync with alwaysgreen-streak-detector.yml.
const MEDIC = {
  '@camunda/test-automation-team': '<!subteam^S09UF0EV0HG|test-automation-medic>',
  '@camunda/distribution': '<!subteam^S053K7C7QKU|distribution-medic>',
  '@camunda/engineering-operations': '<!subteam^S07D6C6B18T|monorepo-ci-medic>',
  '@camunda/core-features': '<!subteam^S08P2CU9V8W|core-features-medic>',
};
// No fallback: an unmapped/missing owner (e.g. the "unknown" default) must not
// silently page monorepo-ci-medic. Returns '' (nothing to concatenate) when
// there's no mapping, so callers don't need to worry about spacing either.
const medicMention = (owner) => (MEDIC[owner] ? ` ${MEDIC[owner]}` : '');

const CATALOG = {
  product: {
    title: 'Real failure — likely your change',
    emoji: '🔴',
    steps: [
      'Open the failing job below and read the logs / screenshots.',
      'Reproduce locally, fix, and re-push.',
    ],
  },
  flaky: {
    title: 'Likely a flaky test — probably not your change',
    emoji: '🟡',
    steps: [
      'Re-run the failed job.',
      'If it reproduces, file a flaky-test issue (label `flaky`) and ping the owner team.',
    ],
  },
  infra: {
    title: 'Infrastructure failure — not your code',
    emoji: '🟠',
    steps: [
      'Re-run the failed job.',
      `If it persists, ping <${RELIABILITY}|#alwaysgreen-reliability> — the owner team triages cluster/runner issues.`,
    ],
  },
  'test-infra': {
    title: 'Test infrastructure issue — not your code',
    emoji: '🟠',
    steps: [
      'Re-run the failed job.',
      `If it persists, ping the owner team — the test environment (cluster, login endpoint) may need attention.`,
    ],
  },
  external: {
    title: 'External dependency failure (registry / Harbor / marketplace)',
    emoji: '🟠',
    steps: [
      "Check the dependency's status, then re-run.",
      'Sustained outage → ping the owner team.',
    ],
  },
  test: {
    title: 'Test / automation bug',
    emoji: '🟣',
    steps: [
      'Owned by Test Automation — not a product regression.',
      `Link this run in <${ASK}|#ask-alwaysgreen>.`,
    ],
  },
  unknown: {
    title: 'Needs triage',
    emoji: '⚪',
    steps: [
      'Open the run and follow the debugging cookbook.',
      `Ask in <${ASK}|#ask-alwaysgreen> if the cause is unclear.`,
    ],
  },
};

function model() {
  const category = (env('FB_CATEGORY') || 'unknown').toLowerCase();
  const c = CATALOG[category] || CATALOG.unknown;
  // Resolve PR number: explicit number, or extract from a merge_group head_ref
  // refs/heads/gh-readonly-queue/<base>/pr-<N>-<sha>.
  let pr = env('FB_PR');
  const m = pr.match(/pr-(\d+)-/);
  if (m) pr = m[1];
  if (!/^\d+$/.test(pr)) pr = '';
  return {
    category,
    ...c,
    stage: env('FB_STAGE', 'unknown'),
    owner: env('FB_OWNER', 'unknown'),
    status: env('FB_STATUS', 'failure'),
    runUrl: env('FB_RUN_URL'),
    evidence: env('FB_EVIDENCE'),
    repo: env('FB_REPO'),
    cookbook: env('FB_COOKBOOK_URL'),
    pr,
  };
}

// ── Renderers (same content, per-surface formatting) ──
function markdown(m) {
  const ev = m.evidence || `Failing stage \`${m.stage}\` — see the run.`;
  const steps = m.steps.map((s) => `- ${slackToMd(s)}`).join('\n');
  const links = [
    m.runUrl && `[Run](${m.runUrl})`,
    m.cookbook && `[Debugging cookbook](${m.cookbook})`,
    `[#ask-alwaysgreen](${ASK})`,
  ]
    .filter(Boolean)
    .join(' · ');
  return `${MARKER}
### ${m.emoji} AlwaysGreen — ${m.title}
**Stage:** \`${m.stage}\` · **Category:** \`${m.category}\` · **Owner:** ${m.owner}

**Evidence:** ${ev}

**Next steps:**
${steps}

${links}`;
}

// Slack mrkdwn (links are <url|text>; bold is *text*)
const slackToMd = (s) => s.replace(/<([^|>]+)\|([^>]+)>/g, '[$2]($1)');
function slackText(m) {
  const ev = m.evidence || `Failing stage \`${m.stage}\` — see the run.`;
  const steps = m.steps.map((s) => `• ${s}`).join('\n');
  const prLink = m.pr && m.repo ? ` · <https://github.com/${m.repo}/pull/${m.pr}|PR #${m.pr}>` : '';
  const links = [
    m.runUrl && `<${m.runUrl}|Run>`,
    m.cookbook && `<${m.cookbook}|Cookbook>`,
    `<${ASK}|#ask-alwaysgreen>`,
  ]
    .filter(Boolean)
    .join(' · ');
  return `${m.emoji} *AlwaysGreen — ${m.title}*\n*Stage:* \`${m.stage}\` · *Category:* \`${m.category}\` · *Owner:* ${m.owner}${medicMention(m.owner)}${prLink}\n*Evidence:* ${ev}\n*Next steps:*\n${steps}\n${links}`;
}

const gh = (args) => exec('gh', args, {maxBuffer: 64 * 1024 * 1024});

async function postSummary(md) {
  const f = process.env.GITHUB_STEP_SUMMARY;
  if (!f) return;
  await appendFile(f, `\n${md}\n`);
  console.log('[feedback] wrote job summary');
}

// Upsert: one bot comment per PR, updated on re-runs (find by MARKER).
async function upsertPrComment(m, md) {
  if (!m.pr || !m.repo || !env('GH_TOKEN')) {
    console.log('[feedback] PR comment skipped (no pr/repo/token)');
    return;
  }
  try {
    const {stdout} = await gh([
      'api',
      `repos/${m.repo}/issues/${m.pr}/comments`,
      '--paginate',
      '--jq',
      '.[] | {id, body}',
    ]);
    const existing = stdout
      .split('\n')
      .filter(Boolean)
      .map((l) => JSON.parse(l))
      .find((c) => (c.body || '').includes(MARKER));
    if (existing) {
      await gh([
        'api',
        '--method',
        'PATCH',
        `repos/${m.repo}/issues/comments/${existing.id}`,
        '-f',
        `body=${md}`,
      ]);
      console.log(`[feedback] updated PR #${m.pr} comment ${existing.id}`);
    } else {
      await gh([
        'api',
        '--method',
        'POST',
        `repos/${m.repo}/issues/${m.pr}/comments`,
        '-f',
        `body=${md}`,
      ]);
      console.log(`[feedback] created PR #${m.pr} comment`);
    }
  } catch (e) {
    console.error(`[feedback] PR comment failed: ${e.message || e}`);
  }
}

async function postSlack(m) {
  const channel = env('FB_SLACK_CHANNEL');
  const token = env('SLACK_BOT_TOKEN');
  if (!channel || !token) {
    console.log('[feedback] Slack skipped (no channel/token)');
    return;
  }
  try {
    const res = await fetch('https://slack.com/api/chat.postMessage', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json; charset=utf-8',
      },
      body: JSON.stringify({channel, text: slackText(m)}),
    });
    const j = await res.json();
    if (!j.ok) throw new Error(j.error || 'unknown');
    console.log(`[feedback] posted to Slack ${channel}`);
  } catch (e) {
    console.error(`[feedback] Slack post failed: ${e.message || e}`);
  }
}

async function main() {
  const m = model();
  const md = markdown(m);
  await postSummary(md);
  await upsertPrComment(m, md);
  await postSlack(m);
}

main().catch((e) => {
  console.error(e);
  // Feedback is best-effort — never fail the CI job because of it.
  process.exit(0);
});
