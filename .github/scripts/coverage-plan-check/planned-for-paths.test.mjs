// Tests for planned-for-paths.mjs — which tells a pull request's author
// which reviewed-but-unwritten coverage cases their changed files own.
//
// Two things these pin, because both are failure modes that destroy the
// feature rather than dent it:
//
//   Reporting a case that is NOT the author's. Being told a case is yours
//   when it is not teaches people to skip the section, and then the ones that
//   ARE theirs go unread too. Every match has to be one a reader would agree
//   with on sight.
//
//   Failing the build. This is advice about work a plan says is outstanding,
//   and a case can be deliberately deferred. A gate here would be answered by
//   deleting the line from the plan.
import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {
  OUTSTANDING,
  outstandingForPaths,
  owningDirs,
  owns,
  render,
} from './planned-for-paths.mjs';

const UI = 'qa/c8-orchestration-cluster-e2e-test-suite/tests/operate';
const JAVA = 'configuration/src/test/java/io/camunda/configuration/data';
const WEB = 'webapp/client/apps/orchestration-cluster-webapp/src/operate/pages/X';

const planWith = (specPath, cases, extra = {}) => ({
  file: 'qa/coverage/plan.yml',
  plan: {epic: 'camunda/product-hub#1', specs: [{path: specPath, ...extra, cases}]},
});

describe('owns', () => {
  it('matches a file beside the spec', () => {
    assert.equal(owns(`${UI}/processes.spec.ts`, `${UI}/batchOperations.spec.ts`), true);
  });

  // The whole reason a bare directory comparison is not enough. A planned
  // JUnit test does not exist, and the author is editing main sources, so
  // without the mirror the commonest case in this repository never matches.
  it('matches a main source against the test package that mirrors it', () => {
    assert.equal(
      owns(
        'configuration/src/main/java/io/camunda/configuration/data/AuditLog.java',
        `${JAVA}/AuditLogTest.java`,
      ),
      true,
    );
  });

  // Foo.test.tsx sits beside Foo.tsx, so the plain comparison already covers
  // the frontend — pinned so nobody "fixes" it by adding a second rule.
  it('matches a component against its sibling test file', () => {
    assert.equal(owns(`${WEB}/X.tsx`, `${WEB}/X.test.tsx`), true);
  });

  it('matches editing the spec itself', () => {
    assert.equal(owns(`${JAVA}/AuditLogTest.java`, `${JAVA}/AuditLogTest.java`), true);
  });

  // A parent, a sibling directory and an unrelated package are all NOT the
  // author's. Matching any of them is how the section becomes noise.
  it('does not match a neighbouring or parent directory', () => {
    assert.equal(owns(`${UI}/nested/deep.spec.ts`, `${UI}/batchOperations.spec.ts`), false);
    assert.equal(
      owns(
        'qa/c8-orchestration-cluster-e2e-test-suite/tests/tasklist/t.spec.ts',
        `${UI}/batchOperations.spec.ts`,
      ),
      false,
    );
    assert.equal(
      owns('configuration/src/main/java/io/camunda/other/Thing.java', `${JAVA}/AuditLogTest.java`),
      false,
    );
  });

  // The mirror is one-directional and anchored. A path that merely contains
  // the words must not be rewritten.
  it('only mirrors a real maven test root', () => {
    assert.deepEqual(owningDirs('src/test/java/Foo.java'), ['src/test/java']);
    assert.deepEqual(owningDirs('a/src/test/java/p/FooTest.java'), [
      'a/src/test/java/p',
      'a/src/main/java/p',
    ]);
  });
});

describe('outstandingForPaths', () => {
  const cases = [
    {id: 'A-1', status: 'planned', surface: 'unit', traces_to: 'one'},
    {id: 'A-2', status: 'automated', surface: 'unit', traces_to: 'two'},
    {id: 'A-3', status: 'stub', surface: 'unit', traces_to: 'three'},
    {id: 'A-4', status: 'excluded', surface: 'unit', traces_to: 'four'},
  ];

  it('reports only the cases that do not run yet', () => {
    const found = outstandingForPaths(
      [planWith(`${JAVA}/AuditLogTest.java`, cases)],
      [`${JAVA}/Other.java`],
    );
    assert.deepEqual(
      found[0].cases.map((c) => c.id),
      ['A-1', 'A-3'],
    );
  });

  // An `automated` case is already covered, and an `excluded` one is a
  // reviewed decision that the test must NOT exist. Surfacing either would
  // argue with a judgement a person already made.
  it('says nothing when every case already runs', () => {
    const only = [{id: 'B-1', status: 'automated'}, {id: 'B-2', status: 'excluded'}];
    assert.deepEqual(
      outstandingForPaths([planWith(`${JAVA}/X.java`, only)], [`${JAVA}/Y.java`]),
      [],
    );
  });

  it('says nothing when the change is somewhere else entirely', () => {
    assert.deepEqual(
      outstandingForPaths([planWith(`${JAVA}/AuditLogTest.java`, cases)], ['README.md']),
      [],
    );
  });

  it('ignores blank lines in the changed-file list', () => {
    const found = outstandingForPaths(
      [planWith(`${JAVA}/AuditLogTest.java`, cases)],
      ['', '   ', `${JAVA}/Other.java`],
    );
    assert.equal(found.length, 1);
    assert.deepEqual(found[0].matchedBy, [`${JAVA}/Other.java`]);
  });

  it('survives a plan with no specs or no cases', () => {
    assert.deepEqual(outstandingForPaths([{file: 'p.yml', plan: {}}], ['a.java']), []);
    assert.deepEqual(
      outstandingForPaths([{file: 'p.yml', plan: {specs: [{path: 'a/b.java'}]}}], ['a/c.java']),
      [],
    );
  });
});

describe('render', () => {
  const found = outstandingForPaths(
    [
      planWith(
        `${JAVA}/AuditLogTest.java`,
        [{id: 'A-1', status: 'planned', surface: 'unit', traces_to: 'does\n  a thing'}],
        {framework: 'junit'},
      ),
    ],
    [`${JAVA}/Other.java`],
  );

  it('names the case, the plan and what matched', () => {
    const md = render(found);
    assert.match(md, /`A-1`/);
    assert.match(md, /qa\/coverage\/plan\.yml/);
    assert.match(md, /Other\.java/);
  });

  // traces_to is free prose from a YAML folded block. A newline breaks the
  // table row it sits in, and a pipe breaks the column.
  it('flattens prose so it cannot break the table', () => {
    const md = render(found);
    const row = md.split('\n').find((l) => l.includes('`A-1`'));
    assert.ok(row.includes('does a thing'), row);
    assert.equal(row.startsWith('|'), true);
  });

  it('escapes a pipe in the prose', () => {
    const md = render(
      outstandingForPaths(
        [planWith('a/b/CTest.java', [{id: 'C-1', status: 'planned', traces_to: 'x | y'}])],
        ['a/b/C.java'],
      ),
    );
    const row = md.split('\n').find((l) => l.includes('`C-1`'));
    assert.ok(row.includes('x \\| y'), row);
  });

  // Nothing matched means nothing is written, not an empty heading. A section
  // that appears on every pull request saying "none" is the kind of noise
  // that gets a check muted.
  it('renders nothing at all when nothing matched', () => {
    assert.equal(render([]), '');
  });
});

describe('OUTSTANDING', () => {
  it('is the two statuses that mean the test does not run', () => {
    assert.deepEqual([...OUTSTANDING].sort(), ['planned', 'stub']);
  });
});
