import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { test } from 'node:test';
import { extractSection, isOptOutTicked, parseRefs } from '../src/parser';

const pullRequestTemplate = readFileSync(
  resolve(process.cwd(), '../../pull_request_template.md'),
  'utf8',
);

// Keep this parser aligned with korthout/backport-action v4.6.0. The action
// scans the raw source PR body, including HTML comments, before replacing
// ${issue_refs} in the generated backport description.
const issueRefPattern = /(?:^| )((?:[^\n #/]+\/[^\n #/]+))?#([1-9][0-9]*)(?: |$)/gm;

function getMentionedIssueRefs(body: string): string[] {
  return body.match(issueRefPattern)?.map((reference) => reference.trim()) ?? [];
}

function renderBackportDescription(
  sourceBody: string,
  sourcePullRequest: number,
  targetBranch: string,
): string {
  const issueRefs = getMentionedIssueRefs(sourceBody).join(' ');
  return [
    `⤵️ Backport of #${sourcePullRequest} → \`${targetBranch}\``,
    '',
    `relates to ${issueRefs}`,
  ].join('\n');
}

test('the PR template contains no issue-like placeholders for backport-action', () => {
  // given
  const generated = renderBackportDescription(pullRequestTemplate, 62512, 'stable/8.9');

  // then
  assert.match(generated, /Backport of #62512 → `stable\/8\.9`/);
  assert.deepEqual(getMentionedIssueRefs(pullRequestTemplate), []);
  assert.doesNotMatch(generated, /#1234/);
});

test('a source PR with one real issue keeps that issue once', () => {
  // given
  const sourceBody = `${pullRequestTemplate}\ncloses #28374`;

  // when
  const generated = renderBackportDescription(sourceBody, 62512, 'stable/8.9');

  // then
  assert.match(generated, /Backport of #62512 → `stable\/8\.9`/);
  assert.equal(generated.match(/#28374/g)?.length, 1);
  assert.match(generated, /relates to #28374/);
});

test('multiple real issues keep their source order and occur once each', () => {
  // given
  const sourceBody = `${pullRequestTemplate}\nrelates to #28374\nfixes #54840`;

  // when
  const generated = renderBackportDescription(sourceBody, 62512, 'stable/8.9');

  // then
  assert.match(generated, /relates to #28374 #54840/);
  assert.equal(generated.match(/#28374/g)?.length, 1);
  assert.equal(generated.match(/#54840/g)?.length, 1);
});

test('the release-notes parser still ignores template boilerplate', () => {
  // given
  const section = extractSection(pullRequestTemplate);

  // then
  assert.ok(section);
  assert.deepEqual(parseRefs(section), []);
  assert.equal(isOptOutTicked(pullRequestTemplate), false);
});
