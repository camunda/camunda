/**
 * Tests for Collector Integration
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { collectTests } from './collector.js';
import { NamingStrategy } from './naming-strategy.js';
import { LocationStrategy } from './location-strategy.js';
import { AnnotationStrategy } from './annotation-strategy.js';
import { writeFileSync, mkdtempSync, mkdirSync, rmSync } from 'fs';
import { join } from 'path';
import { tmpdir } from 'os';

/**
 * Helper to create a temporary repository with a .codeowners file
 */
function createTempRepo() {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  // Create a minimal .codeowners file so the collector can resolve ownership
  writeFileSync(join(tempRepo, '.codeowners'), '* @test-team\n');
  return tempRepo;
}

test('Collector - applies strategies in order', async () => {
  // Create a temporary repository structure
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  // Create a test file
  const testFile = join(testDir, 'UserServiceTest.java');
  writeFileSync(testFile, `
package io.camunda.service;

import org.junit.jupiter.api.Test;

public class UserServiceTest {
  @Test
  void testSomething() {
  }
}
`);

  try {
    // Use naming + location strategies
    const strategies = [
      new NamingStrategy(),
      new LocationStrategy()
    ];

    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    // Should find one test
    assert.equal(result.tests.length, 1);

    // Should be identified as a test
    const test = result.tests[0];
    assert.match(test.path, /UserServiceTest\.java$/);

    // Should be classified as unit test by naming classifier
    assert.equal(test.testType, 'unit');

    // Should have owners
    assert.ok(Array.isArray(test.owners));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - naming strategy identifies tests and sets test type', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  const testFile = join(testDir, 'UserServiceTest.java');
  writeFileSync(testFile, 'public class UserServiceTest {}');

  try {
    const strategies = [new NamingStrategy()];
    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    assert.equal(result.tests.length, 1);
    const test = result.tests[0];
    assert.match(test.path, /UserServiceTest\.java$/);
    // Naming classifier sets testType to 'unit' for *Test.java
    assert.equal(test.testType, 'unit');
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - location strategy enriches test type for QA tests', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'qa', 'integration-tests', 'src', 'test', 'java', 'io', 'camunda');
  mkdirSync(testDir, { recursive: true });

  const testFile = join(testDir, 'UserServiceTest.java');
  writeFileSync(testFile, 'public class UserServiceTest {}');

  try {
    const strategies = [
      new NamingStrategy(),
      new LocationStrategy()
    ];

    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    assert.equal(result.tests.length, 1);
    const test = result.tests[0];
    assert.match(test.path, /UserServiceTest\.java$/);
    // Location classifier overrides naming classifier for qa/ paths
    assert.equal(test.testType, 'integration');
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - handles multiple files', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(testDir, 'AccountServiceTest.java'), 'public class AccountServiceTest {}');
  writeFileSync(join(testDir, 'PaymentServiceIT.java'), 'public class PaymentServiceIT {}');

  try {
    const strategies = [
      new NamingStrategy(),
      new LocationStrategy()
    ];

    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    assert.equal(result.tests.length, 3);

    // Check that all are identified correctly
    const unitTests = result.tests.filter(t => t.testType === 'unit');
    const integrationTests = result.tests.filter(t => t.testType === 'integration');

    assert.equal(unitTests.length, 2);
    assert.equal(integrationTests.length, 1);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - generates warnings for unclassified tests', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  // Create a file identified as test by annotation but no testType
  const testFile = join(testDir, 'WeirdTest.java');
  writeFileSync(testFile, `
package io.camunda.service;

import org.junit.jupiter.api.Test;

public class WeirdTest {
  @Test
  void test() {}
}
`);

  try {
    const strategies = [
      new AnnotationStrategy()
      // No naming or location classifier, so testType stays null
    ];

    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    assert.equal(result.tests.length, 1);

    // Should have a warning about unclassified test
    const unclassifiedWarnings = result.warnings.filter(w => w.type === 'unclassified');
    assert.equal(unclassifiedWarnings.length, 1);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - tracks utilities separately from tests', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  // Create a test file
  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');

  // Create utility files
  writeFileSync(join(testDir, 'TestUtil.java'), 'public class TestUtil {}');
  writeFileSync(join(testDir, 'TestData.java'), 'public class TestData {}');

  try {
    const strategies = [new NamingStrategy()];
    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    // Should find 1 test and 2 utilities
    assert.equal(result.tests.length, 1);
    assert.equal(result.utilities.length, 2);

    // Check test
    assert.match(result.tests[0].path, /UserServiceTest\.java$/);

    // Check utilities
    const utilityNames = result.utilities.map(u => u.path.split('/').pop()).sort();
    assert.deepEqual(utilityNames, ['TestData.java', 'TestUtil.java']);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - annotation classifier identifies utilities by @TestConfiguration', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  const configFile = join(testDir, 'TestConfig.java');
  writeFileSync(configFile, `
package io.camunda.service;

import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestConfig {
}
`);

  try {
    const strategies = [new AnnotationStrategy()];
    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    assert.equal(result.tests.length, 0);
    assert.equal(result.utilities.length, 1);
    assert.match(result.utilities[0].path, /TestConfig\.java$/);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - filters non-test and non-utility files', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(testDir, 'SomeHelper.java'), 'public class SomeHelper {}');
  writeFileSync(join(testDir, 'RandomClass.java'), 'public class RandomClass {}');

  try {
    const strategies = [new NamingStrategy()];
    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    // Should only find the test, not the other files
    assert.equal(result.tests.length, 1);
    assert.equal(result.utilities.length, 0);
    assert.match(result.tests[0].path, /UserServiceTest\.java$/);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('Collector - enrichment chain preserves earlier classifications', async () => {
  const tempRepo = createTempRepo();
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda', 'service');
  mkdirSync(testDir, { recursive: true });

  const testFile = join(testDir, 'UserServiceIT.java');
  writeFileSync(testFile, `
package io.camunda.service;

import org.junit.jupiter.api.Test;

public class UserServiceIT {
  @Test
  void test() {}
}
`);

  try {
    // Naming classifier identifies as integration test
    // Annotation classifier should preserve that
    const strategies = [
      new NamingStrategy(),
      new AnnotationStrategy()
    ];

    const result = await collectTests(tempRepo, strategies, { skipCodeownersCheck: true });

    assert.equal(result.tests.length, 1);
    assert.equal(result.tests[0].testType, 'integration');
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});
