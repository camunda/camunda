// Tests for the scoped-run verdict.
//
// Run: node --test .github/scripts/scoped-run-junit-verdict.test.mjs
//
// The fixtures are the shapes the two tools actually write, taken from running
// them rather than imagined: vitest nests `describe` titles into the name and
// escapes the separator, Surefire writes a method's @DisplayName as the name
// and omits the <testsuites> wrapper for a single class.
import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {
  leafName,
  parseCases,
  stripNonStructure,
  wellFormed,
} from './scoped-run-junit-verdict.mjs';

const VITEST_NESTED = `<?xml version="1.0" encoding="UTF-8" ?>
<testsuites name="vitest tests" tests="3" failures="0" errors="0">
  <testsuite name="src/a.test.ts" tests="3" failures="0" errors="0" skipped="0">
    <testcase classname="src/a.test.ts" name="Audit log &gt; AL-UI-01: works inside a describe" time="0.001">
    </testcase>
    <testcase classname="src/a.test.ts" name="Audit log &gt; nested deeper &gt; AL-UI-03: two levels down" time="0.001">
    </testcase>
    <testcase classname="src/a.test.ts" name="AL-UI-02: at the top level" time="0.001">
    </testcase>
  </testsuite>
</testsuites>`;

const SUREFIRE = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="io.camunda.X" tests="2" errors="0" skipped="0" failures="0">
  <testcase name="PROBE-01: carries the case id in its display name" classname="io.camunda.X" time="0.104"/>
  <testcase name="shouldHaveNoDisplayName" classname="io.camunda.X" time="0.008"/>
</testsuite>`;

describe('leafName', () => {
  // vitest names a test after the describes enclosing it. Reading the whole
  // string missed the id on every test that was not top-level, which is most
  // of them.
  it('takes the last segment of a nested vitest name', () => {
    assert.equal(
      leafName('Audit log &gt; AL-UI-01: works'),
      'AL-UI-01: works',
    );
    assert.equal(
      leafName('Audit log &gt; nested &gt; AL-UI-03: deep'),
      'AL-UI-03: deep',
    );
  });

  it('leaves an unnested name alone', () => {
    assert.equal(leafName('AL-UI-02: top level'), 'AL-UI-02: top level');
    assert.equal(leafName('shouldDoSomething'), 'shouldDoSomething');
  });

  it('decodes the entities the name is written with', () => {
    assert.equal(leafName('a &amp; b'), 'a & b');
    assert.equal(leafName('x &lt;y&gt; z'), 'x <y> z');
  });

  it('survives an absent name', () => {
    assert.equal(leafName(undefined), '');
    assert.equal(leafName(null), '');
  });
});

describe('parseCases', () => {
  it('finds a nested vitest case by its leaf', () => {
    const cases = parseCases(stripNonStructure(VITEST_NESTED));
    const ids = cases.map((c) => c.leaf.split(':')[0]);
    assert.deepEqual(ids, ['AL-UI-01', 'AL-UI-03', 'AL-UI-02']);
    assert.equal(
      cases.every((c) => !c.failed && !c.skipped),
      true,
    );
  });

  it('reads a Surefire document that has no testsuites wrapper', () => {
    const cases = parseCases(stripNonStructure(SUREFIRE));
    assert.equal(cases.length, 2);
    assert.equal(cases[0].leaf, 'PROBE-01: carries the case id in its display name');
    // A method with no @DisplayName falls back to its identifier, so a
    // generated test that forgets the annotation is not matched by accident.
    assert.equal(cases[1].leaf, 'shouldHaveNoDisplayName');
  });

  // Tests log, and what they log lands in system-out. A regex cannot tell a
  // tag from a tag-shaped string, so a passing case whose output mentioned
  // <error> was read as a failure.
  it('does not read logged markup as an outcome', () => {
    const xml = `<testsuite name="T">
  <testcase name="AL-UI-01: passes but logs xml" classname="T" time="0.1">
    <system-out><![CDATA[ logged: <error>handled</error> and <skipped>a thing</skipped> ]]></system-out>
  </testcase>
</testsuite>`;
    const [only] = parseCases(stripNonStructure(xml));
    assert.equal(only.failed, false);
    assert.equal(only.skipped, false);
  });

  it('still reads a real failure and a real skip', () => {
    const xml = `<testsuite name="T">
  <testcase name="A-01: broken" classname="T"><failure message="boom">x</failure></testcase>
  <testcase name="A-02: skipped" classname="T"><skipped/></testcase>
</testsuite>`;
    const [failed, skipped] = parseCases(stripNonStructure(xml));
    assert.equal(failed.failed, true);
    assert.equal(skipped.skipped, true);
  });

  it('ignores a comment that contains markup', () => {
    const xml = `<testsuite name="T">
  <testcase name="A-01: fine" classname="T"><!-- <failure/> --></testcase>
</testsuite>`;
    const [only] = parseCases(stripNonStructure(xml));
    assert.equal(only.failed, false);
  });
});

describe('wellFormed', () => {
  // A report truncated mid-write can hold a passing case and have lost the
  // failure that came after it. Reading it turns a dead run into a green
  // verdict, which is the answer this whole script exists to refuse.
  it('rejects a truncated document', () => {
    assert.match(
      wellFormed('<testsuite><testcase name="A-01: works"/>') ?? '',
      /unbalanced <testsuite>/,
    );
  });

  it('rejects an unclosed testcase', () => {
    assert.match(
      wellFormed('<testsuite><testcase name="A-01: works"></testsuite>') ?? '',
      /unbalanced <testcase>/,
    );
  });

  it('accepts both tools’ complete documents', () => {
    assert.equal(wellFormed(stripNonStructure(VITEST_NESTED)), null);
    assert.equal(wellFormed(stripNonStructure(SUREFIRE)), null);
  });
});
