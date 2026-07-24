import assert from 'node:assert/strict';
import { test } from 'node:test';
import { extractSection, isOptOutTicked, parseRefs } from '../src/parser';

test('extractSection slices the Related issues body up to the next heading', () => {
  const body = ['## Description', 'x', '## Related issues', 'closes #12', '', '## Checklist', '- [ ] a'].join('\n');
  assert.equal(extractSection(body), 'closes #12\n');
});

test('extractSection is case-insensitive and returns null when absent', () => {
  assert.equal(extractSection('## RELATED ISSUES\ncloses #1'), 'closes #1');
  assert.equal(extractSection('## Something else\ncloses #1'), null);
});

test('extractSection reads to EOF when no trailing heading', () => {
  assert.equal(extractSection('## Related issues\ncloses #7'), 'closes #7');
});

test('parseRefs classifies every closing keyword variant', () => {
  for (const kw of ['close', 'closes', 'closed', 'fix', 'fixes', 'fixed', 'resolve', 'resolves', 'resolved', 'completes']) {
    const [ref] = parseRefs(`${kw} #42`);
    assert.equal(ref?.kind, 'closing', `${kw} should be closing`);
    assert.equal(ref?.number, 42);
  }
});

test('parseRefs treats "relates to" and bare #N as contributor', () => {
  assert.equal(parseRefs('relates to #5')[0]?.kind, 'contributor');
  assert.equal(parseRefs('see #5 for context')[0]?.kind, 'contributor');
});

test('parseRefs marks "Backport of #N" as backport', () => {
  const [ref] = parseRefs('Backport of #99');
  assert.equal(ref?.kind, 'backport');
  assert.equal(ref?.number, 99);
});

test('parseRefs records the explicit owner/repo prefix (cross-repo left to resolver)', () => {
  const [ref] = parseRefs('closes camunda/other#7');
  assert.equal(ref?.repo, 'camunda/other');
  assert.equal(ref?.number, 7);
});

test('parseRefs parses full GitHub URLs', () => {
  const refs = parseRefs('fixes https://github.com/camunda/camunda/issues/321');
  assert.equal(refs[0]?.number, 321);
  assert.equal(refs[0]?.kind, 'closing');
  assert.equal(refs[0]?.repo, 'camunda/camunda');
});

test('parseRefs ignores markdown headings and colon separators', () => {
  assert.equal(parseRefs('## Related issues').length, 0);
  assert.equal(parseRefs('closes: #8')[0]?.number, 8);
});

test('HTML comments are stripped so template boilerplate is not parsed as a ref', () => {
  // The PR template ships an instructional comment inside "## Related issues".
  const body = ['## Related issues', '<!-- e.g. closes #1234 to auto-close on merge -->', ''].join('\n');
  assert.equal(parseRefs(extractSection(body) ?? '').length, 0);
  // A multi-line comment naming a ref is likewise ignored.
  const multiline = 'closes #1234\nline\n-->';
  assert.equal(parseRefs(`<!-- ${multiline}`).length, 0);
});

test('a commented-out opt-out checkbox does not count as ticked', () => {
  assert.equal(isOptOutTicked('<!-- - [x] This PR does not need a linked issue -->'), false);
});

test('isOptOutTicked only fires on a ticked checkbox with the phrase', () => {
  assert.equal(isOptOutTicked('- [x] This PR does not need a linked issue'), true);
  assert.equal(isOptOutTicked('- [ ] This PR does not need a linked issue'), false);
  assert.equal(isOptOutTicked('This PR does not need a linked issue'), false);
});
