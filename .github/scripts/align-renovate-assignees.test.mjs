import {test} from 'node:test';
import assert from 'node:assert/strict';

import {
  isRenovateAuthor,
  normalizeTitle,
  groupSiblings,
  computeSeedAssignees,
  computeAssigneeDiff,
} from './align-renovate-assignees.mjs';

test('isRenovateAuthor matches Renovate under its login spellings', () => {
  assert.equal(isRenovateAuthor({login: 'app/renovate'}), true);
  assert.equal(isRenovateAuthor({login: 'renovate[bot]'}), true);
  assert.equal(isRenovateAuthor({login: 'some-renovate-fork', is_bot: true}), true);
  assert.equal(isRenovateAuthor({login: 'octocat'}), false);
  assert.equal(isRenovateAuthor(null), false);
});

test('normalizeTitle strips only the exact trailing base-branch suffix', () => {
  const title = 'deps: Update dependency io.netty:netty-bom to v4.2.19.Final';
  assert.equal(normalizeTitle(`${title} (main)`, 'main'), title);
  assert.equal(normalizeTitle(`${title} (stable/8.9)`, 'stable/8.9'), title);
  // A trailing parenthesis that is not this PR's base ref is left untouched,
  // so unrelated titles are never merged.
  assert.equal(normalizeTitle('Update X (major)', 'main'), 'Update X (major)');
});

test('groupSiblings buckets same-dependency PRs across base branches together', () => {
  const base = 'deps: Update dependency io.netty:netty-bom to v4.2.19.Final';
  const prs = [
    {number: 1, title: `${base} (main)`, baseRefName: 'main'},
    {number: 2, title: `${base} (stable/8.9)`, baseRefName: 'stable/8.9'},
    {number: 3, title: `${base} (stable/8.10)`, baseRefName: 'stable/8.10'},
    {number: 4, title: 'deps: Update dependency guava to v33 (main)', baseRefName: 'main'},
  ];
  const groups = groupSiblings(prs);
  assert.equal(groups.get(base).length, 3);
  assert.equal(groups.size, 2);
});

test('computeSeedAssignees picks the lowest-numbered assigned PR', () => {
  const group = [
    {number: 30, assignees: [{login: 'carol'}]},
    {number: 10, assignees: [{login: 'alice'}]},
    {number: 20, assignees: [{login: 'bob'}]},
  ];
  assert.deepEqual(computeSeedAssignees(group), ['alice']);
});

test('computeSeedAssignees ignores unassigned PRs and returns [] when none assigned', () => {
  assert.deepEqual(
    computeSeedAssignees([
      {number: 5, assignees: []},
      {number: 9, assignees: [{login: 'dave'}]},
    ]),
    ['dave'],
  );
  assert.deepEqual(
    computeSeedAssignees([
      {number: 5, assignees: []},
      {number: 9, assignees: []},
    ]),
    [],
  );
});

test('computeAssigneeDiff reports the add/remove to reach the desired set', () => {
  assert.deepEqual(computeAssigneeDiff(['bob'], ['alice']), {
    toAdd: ['alice'],
    toRemove: ['bob'],
  });
  assert.deepEqual(computeAssigneeDiff([], ['alice']), {
    toAdd: ['alice'],
    toRemove: [],
  });
  assert.deepEqual(computeAssigneeDiff(['alice'], ['alice']), {
    toAdd: [],
    toRemove: [],
  });
});
