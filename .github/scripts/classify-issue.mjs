#!/usr/bin/env node
/**
 * classify-issue.mjs
 *
 * Classifies GitHub issues that lack a component/* label using Claude AI,
 * then routes them to the correct project board.
 *
 * Modes:
 *   --scan [--limit N]   Paginate all open issues, classify orphans (default limit: unlimited)
 *   --issue <N>          Classify a single issue by number
 *   --backtest [--limit N]  Test accuracy against N already-labeled issues (default: 100)
 *
 * Required env:
 *   GH_TOKEN             GitHub API token with issues:write and projects:write
 *   ANTHROPIC_API_KEY    Claude API key
 *
 * Optional env:
 *   DRY_RUN                  'true' to skip label/project writes (default: 'false')
 *   OWNER                    GitHub org (default: 'camunda')
 *   REPO                     GitHub repo (default: 'camunda')
 *   GITHUB_STEP_SUMMARY      Written by GHA; path to append job summary markdown
 */

import Anthropic from '@anthropic-ai/sdk';
import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';

const OWNER = process.env.OWNER ?? 'camunda';
const REPO = process.env.REPO ?? 'camunda';
const DRY_RUN = process.env.DRY_RUN === 'true';

// Mirrors add-to-projects.yml: component label → project number
const COMPONENT_TO_PROJECT = {
  'component/zeebe': 92,
  'component/operate': 173,
  'component/tasklist': 173,
  'component/optimize': 173,
  'component/data-layer': 184,
  'component/connectors': 23,
  'component/identity': 209,
  'component/management-identity': 209,
  'component/c8run': 33,
  'component/feel-js': 79,
  'component/feel-scala': 79,
  'component/build-pipeline': 115,
  'component/release': 115,
  'component/load-tests': 202,
  'component/clients': 182,
  'component/spring-boot-starter': 182,
  'component/camunda-process-test': 182,
  'component/c8-api': 182,
};

const COMPONENT_DESCRIPTIONS = {
  'component/zeebe': 'Core BPMN/DMN process engine, broker, job streaming, protocol (zeebe/)',
  'component/operate': 'Process monitoring web application (operate/)',
  'component/tasklist': 'User task management web application (tasklist/)',
  'component/optimize': 'Process analytics and reporting (optimize/)',
  'component/data-layer': 'Database layer: RDBMS, Elasticsearch, OpenSearch (db/, search/)',
  'component/connectors': 'Out-of-the-box connectors to external systems',
  'component/identity': 'Authentication and authorization service (identity/)',
  'component/management-identity': 'Management-level identity and org auth',
  'component/c8run': 'Packaged local Camunda 8 distribution (c8run/)',
  'component/feel-js': 'FEEL expression language JavaScript implementation',
  'component/feel-scala': 'FEEL expression language Scala implementation',
  'component/build-pipeline': 'CI/CD, GitHub Actions, Maven build infrastructure (.github/)',
  'component/release': 'Release process, versioning, release automation',
  'component/load-tests': 'Cluster load and reliability tests (load-tests/)',
  'component/clients': 'Java and other client libraries (clients/)',
  'component/spring-boot-starter': 'Spring Boot starter for Camunda (clients/)',
  'component/camunda-process-test': 'Process testing library (testing/)',
  'component/c8-api': 'Camunda 8 REST API gateway and public API contracts (gateways/)',
};

const VALID_LABELS = Object.keys(COMPONENT_TO_PROJECT);
const anthropic = new Anthropic();

// --- GitHub GraphQL ---

async function graphql(query, variables = {}) {
  const resp = await fetch('https://api.github.com/graphql', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${process.env.GH_TOKEN}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, variables }),
  });
  const json = await resp.json();
  if (json.errors) throw new Error(`GraphQL: ${JSON.stringify(json.errors)}`);
  return json.data;
}

const ISSUE_FIELDS = `
  id
  number
  title
  body
  createdAt
  url
  labels(first: 30) { nodes { name } }
  projectItems(first: 10) {
    nodes { project { number closed title } }
  }
  parent {
    number
    projectItems(first: 5) {
      nodes { project { number closed title } }
    }
  }
  timelineItems(first: 30, itemTypes: [UNLABELED_EVENT]) {
    nodes {
      ... on UnlabeledEvent {
        createdAt
        actor { login }
        label { name }
      }
    }
  }
`;

async function fetchIssue(issueNumber) {
  const data = await graphql(
    `query($owner: String!, $repo: String!, $number: Int!) {
      repository(owner: $owner, name: $repo) {
        issue(number: $number) { ${ISSUE_FIELDS} }
      }
    }`,
    { owner: OWNER, repo: REPO, number: issueNumber },
  );
  return data.repository.issue;
}

async function* fetchAllOpenIssues() {
  let cursor = null;
  while (true) {
    const data = await graphql(
      `query($owner: String!, $repo: String!, $after: String) {
        repository(owner: $owner, name: $repo) {
          issues(states: OPEN, first: 100, after: $after) {
            pageInfo { hasNextPage endCursor }
            nodes { ${ISSUE_FIELDS} }
          }
        }
      }`,
      { owner: OWNER, repo: REPO, after: cursor },
    );
    const page = data.repository.issues;
    for (const issue of page.nodes) yield issue;
    if (!page.pageInfo.hasNextPage) break;
    cursor = page.pageInfo.endCursor;
  }
}

// --- Issue predicates ---

function hasActiveBoard(issue) {
  return issue.projectItems.nodes.some(n => !n.project.closed);
}

function getComponentLabel(issue) {
  return issue.labels.nodes.find(l => VALID_LABELS.includes(l.name))?.name ?? null;
}

function hasLabel(issue, name) {
  return issue.labels.nodes.some(l => l.name === name);
}

function isRecentlyCreated(issue) {
  const tenMinutesAgo = new Date(Date.now() - 10 * 60 * 1000);
  return new Date(issue.createdAt) > tenMinutesAgo;
}

// --- AI classification ---

async function classifyWithAI(issue) {
  const activeParentBoards = issue.parent?.projectItems?.nodes
    ?.filter(n => !n.project.closed)
    ?.map(n => `project #${n.project.number} "${n.project.title}"`)
    ?.join(', ');

  const removedComponentLabels = issue.timelineItems?.nodes
    ?.filter(n => n.label?.name?.startsWith('component/'))
    ?.map(n => `"${n.label.name}" removed by @${n.actor?.login ?? 'unknown'} on ${n.createdAt}`)
    ?.join('\n  ');

  const labelList = VALID_LABELS
    .map(l => `  ${l}: ${COMPONENT_DESCRIPTIONS[l]}`)
    .join('\n');

  const lines = [
    'Classify this GitHub issue from the Camunda 8 monorepo to exactly one component label.',
    '',
    'Available labels:',
    labelList,
    '',
    `Title: ${issue.title}`,
    `Body: ${(issue.body ?? '').slice(0, 800) || '(empty)'}`,
    `Labels already on issue: ${issue.labels.nodes.map(l => l.name).join(', ') || 'none'}`,
  ];
  if (activeParentBoards) lines.push(`Parent issue belongs to: ${activeParentBoards}`);
  if (removedComponentLabels) {
    lines.push(
      `Previously removed component labels (do NOT re-apply):`,
      `  ${removedComponentLabels}`,
    );
  }

  const response = await anthropic.messages.create({
    model: 'claude-sonnet-4-6',
    max_tokens: 256,
    tools: [{
      name: 'classify',
      description: 'Classify the issue to a component label',
      input_schema: {
        type: 'object',
        properties: {
          label: {
            type: 'string',
            enum: [...VALID_LABELS, 'none'],
            description: 'Best-matching component label, or "none" if uncertain',
          },
          reasoning: {
            type: 'array',
            items: { type: 'string' },
            description: 'Up to 2 brief bullet points explaining the classification',
            maxItems: 2,
          },
        },
        required: ['label', 'reasoning'],
      },
    }],
    tool_choice: { type: 'tool', name: 'classify' },
    messages: [{ role: 'user', content: lines.join('\n') }],
  });

  const toolUse = response.content.find(b => b.type === 'tool_use');
  return toolUse.input;
}

// --- GitHub mutations ---

function gh(...args) {
  return execFileSync('gh', args, {
    env: { ...process.env },
    encoding: 'utf-8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

function addLabels(issueNumber, labels) {
  gh('issue', 'edit', String(issueNumber), '--add-label', labels.join(','), '--repo', `${OWNER}/${REPO}`);
}

function postComment(issueNumber, body) {
  gh('issue', 'comment', String(issueNumber), '--body', body, '--repo', `${OWNER}/${REPO}`);
}

function addToProject(projectNumber, issueUrl) {
  gh('project', 'item-add', String(projectNumber), '--owner', OWNER, '--url', issueUrl);
}

// --- Comment templates ---

function successComment(label, reasoning, applied) {
  const status = applied ? `Auto-classified as \`${label}\`.` : `**Proposed classification**: \`${label}\` *(not yet applied — pending review)*`;
  return [
    '> [!NOTE]',
    `> ${status}`,
    ...reasoning.map(r => `> - ${r}`),
    '>',
    '> Remove the `ai-classified` label to trigger reclassification.',
  ].join('\n');
}

function failureComment() {
  return [
    '> [!WARNING]',
    '> Could not automatically determine a component label for this issue.',
    '> It has been tagged with `needs component label` for manual review.',
    '>',
    '> Remove the `ai-classified` label if you\'d like the classifier to try again.',
  ].join('\n');
}

// --- Case handlers ---

async function handleCaseOne(issue) {
  console.log(`[#${issue.number}] No component label — running AI classification`);
  const { label, reasoning } = await classifyWithAI(issue);

  if (label === 'none') {
    console.log(`[#${issue.number}] AI returned none — escalating`);
    if (!DRY_RUN) addLabels(issue.number, ['ai-classified', 'needs component label']);
    postComment(issue.number, failureComment());
    return { action: 'escalated', label: 'needs component label', project: null };
  }

  console.log(`[#${issue.number}] AI classified as ${label}`);
  if (!DRY_RUN) addLabels(issue.number, ['ai-classified', label]);
  postComment(issue.number, successComment(label, reasoning, !DRY_RUN));
  return { action: DRY_RUN ? 'proposed' : 'classified', label, project: COMPONENT_TO_PROJECT[label] };
}

async function handleCaseTwo(issue, componentLabel) {
  const projectNumber = COMPONENT_TO_PROJECT[componentLabel];
  if (!projectNumber) {
    console.log(`[#${issue.number}] No project mapping for ${componentLabel} — skipping`);
    return { action: 'no-mapping', label: componentLabel, project: null };
  }
  console.log(`[#${issue.number}] Has ${componentLabel} but no board — adding to project #${projectNumber}`);
  if (!DRY_RUN) addToProject(projectNumber, issue.url);
  return { action: DRY_RUN ? 'proposed-direct' : 'direct-assigned', label: componentLabel, project: projectNumber };
}

// --- Modes ---

async function runScan(limit) {
  const results = [];
  let scanned = 0;

  for await (const issue of fetchAllOpenIssues()) {
    scanned++;
    if (hasLabel(issue, 'ai-classified')) continue;
    if (isRecentlyCreated(issue)) continue;
    if (hasActiveBoard(issue)) continue;
    if (limit != null && results.length >= limit) break;

    const componentLabel = getComponentLabel(issue);
    const result = componentLabel
      ? await handleCaseTwo(issue, componentLabel)
      : await handleCaseOne(issue);

    results.push({ issue: issue.number, ...result });
  }

  console.log(`Scanned ${scanned} issues. Processed ${results.length} orphans.`);
  writeJobSummary(results);
  return results;
}

async function runSingle(issueNumber) {
  const issue = await fetchIssue(issueNumber);
  if (hasLabel(issue, 'ai-classified')) {
    console.log(`[#${issueNumber}] Already has ai-classified — skipping`);
    return;
  }
  if (hasActiveBoard(issue)) {
    console.log(`[#${issueNumber}] Already on an active board — skipping`);
    return;
  }
  const componentLabel = getComponentLabel(issue);
  if (componentLabel) {
    console.log(`[#${issueNumber}] Already has component label ${componentLabel} — skipping`);
    return;
  }
  await handleCaseOne(issue);
}

async function runBacktest(limit) {
  let correct = 0;
  let total = 0;
  const mismatches = [];

  for await (const issue of fetchAllOpenIssues()) {
    if (total >= limit) break;
    const trueLabel = getComponentLabel(issue);
    if (!trueLabel) continue;
    total++;

    const fakeIssue = {
      ...issue,
      labels: { nodes: issue.labels.nodes.filter(l => !l.name.startsWith('component/')) },
    };
    const { label: predicted } = await classifyWithAI(fakeIssue);
    const correct_ = predicted === trueLabel;
    if (correct_) correct++;
    else mismatches.push({ issue: issue.number, true: trueLabel, predicted });
    console.log(`[#${issue.number}] true=${trueLabel} predicted=${predicted} ${correct_ ? '✓' : '✗'}`);
  }

  const pct = total > 0 ? ((correct / total) * 100).toFixed(1) : '0.0';
  console.log(`\nAccuracy: ${correct}/${total} (${pct}%)`);
  if (mismatches.length) {
    console.log('\nMismatches:');
    for (const m of mismatches) console.log(`  #${m.issue}: true=${m.true} predicted=${m.predicted}`);
  }
}

// --- Job summary ---

function writeJobSummary(results) {
  const path = process.env.GITHUB_STEP_SUMMARY;
  if (!path) return;

  const now = new Date().toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
  const counts = {
    classified: results.filter(r => r.action === 'classified').length,
    'direct-assigned': results.filter(r => r.action === 'direct-assigned').length,
    proposed: results.filter(r => r.action === 'proposed' || r.action === 'proposed-direct').length,
    escalated: results.filter(r => r.action === 'escalated').length,
  };

  const ACTION_LABEL = {
    classified: 'AI classified',
    proposed: 'AI proposed (review mode)',
    'proposed-direct': 'Direct assign (review mode)',
    'direct-assigned': 'Direct assignment',
    escalated: 'Needs review',
    'no-mapping': 'No project mapping',
  };

  const rows = results.map(r =>
    `| [#${r.issue}](https://github.com/${OWNER}/${REPO}/issues/${r.issue}) | ${ACTION_LABEL[r.action] ?? r.action} | \`${r.label}\` | ${r.project ? `#${r.project}` : '—'} |`,
  );

  const summary = [
    `## Issue Classification Run — ${now}`,
    `| Issue | Action | Label | Project |`,
    `|-------|--------|-------|---------|`,
    ...rows,
    '',
    `**${results.length} orphans processed.** ` +
    `${counts.classified} classified, ${counts['direct-assigned']} directly assigned, ` +
    `${counts.proposed} proposed (review mode), ${counts.escalated} escalated for manual review.`,
  ].join('\n');

  appendFileSync(path, summary + '\n\n');
}

// --- Entry point ---

const argv = process.argv.slice(2);
const limitIdx = argv.indexOf('--limit');
const limit = limitIdx >= 0 ? parseInt(argv[limitIdx + 1], 10) : null;

if (argv.includes('--backtest')) {
  await runBacktest(limit ?? 100);
} else if (argv.includes('--scan')) {
  await runScan(limit);
} else if (argv.includes('--issue')) {
  const n = parseInt(argv[argv.indexOf('--issue') + 1], 10);
  if (isNaN(n)) { console.error('Invalid issue number'); process.exit(1); }
  await runSingle(n);
} else {
  console.error('Usage: classify-issue.mjs --scan [--limit N] | --issue <N> | --backtest [--limit N]');
  process.exit(1);
}
