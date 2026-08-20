/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  FullConfig,
  FullResult,
  Reporter,
  Suite,
  TestCase,
} from '@playwright/test/reporter';

/**
 * CI guard for TestRail's `custom_automation_id` 250-byte limit.
 *
 * The `Publish test results to TestRail` step in the nightly API/E2E
 * workflows uses `trcli parse_junit`, which derives each case's
 * `custom_automation_id` as `"<classname>.<name>"` — exactly
 * `<spec file path>.<describe chain › test title>` from the Playwright
 * JUnit reporter. TestRail rejects any value over 250 bytes and aborts
 * the whole `add_case` batch, so a single over-long test name fails the
 * entire nightly run (hours after merge, on a step unrelated to whether
 * the test itself passes).
 *
 * This reporter recomputes that id with the same `test.titlePath()` API
 * the JUnit reporter uses and fails fast at PR time, with an actionable
 * message, before the test name can ever break the nightly.
 *
 * Run via `--list` so no tests actually execute:
 *   playwright test --list --reporter=./scripts/check-automation-id-length.ts
 */

// TestRail field limit. trcli auto-truncates the case *title* to this
// length but intentionally never truncates the automation_id (it is the
// stable matching key), so we must keep it within budget ourselves.
const MAX_AUTOMATION_ID_BYTES = 250;

function automationIdFor(test: TestCase): string {
  // Playwright JUnit reporter: classname = titlePath()[2] (spec file path),
  // name = titlePath().slice(3).join(' › ') (describe chain + test title).
  const path = test.titlePath();
  const classname = path[2] ?? '';
  const name = path.slice(3).join(' › ');
  return `${classname}.${name}`;
}

class AutomationIdLengthReporter implements Reporter {
  private readonly violations = new Map<string, number>();

  onBegin(_config: FullConfig, suite: Suite): void {
    for (const test of suite.allTests()) {
      const id = automationIdFor(test);
      const bytes = Buffer.byteLength(id, 'utf8');
      // Dedupe: the same id appears once per project.
      if (bytes > MAX_AUTOMATION_ID_BYTES && !this.violations.has(id)) {
        this.violations.set(id, bytes);
      }
    }
  }

  async onEnd(): Promise<{status: FullResult['status']}> {
    if (this.violations.size === 0) {
      console.log(
        `✓ TestRail automation_id check: all test ids are within ${MAX_AUTOMATION_ID_BYTES} bytes.`,
      );
      return {status: 'passed'};
    }

    console.error(
      `\n✗ TestRail automation_id check failed: ${this.violations.size} test(s) exceed the ${MAX_AUTOMATION_ID_BYTES}-byte limit.\n` +
        `  trcli would reject these and abort the entire "Publish test results to TestRail" step.\n` +
        `  Shorten the test title and/or its enclosing describe blocks.\n`,
    );
    for (const [id, bytes] of this.violations) {
      console.error(`  [${bytes} bytes] ${id}`);
    }
    console.error('');
    return {status: 'failed'};
  }
}

export default AutomationIdLengthReporter;
