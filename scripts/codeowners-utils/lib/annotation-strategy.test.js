/**
 * Tests for Annotation Strategy
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { AnnotationStrategy } from './annotation-strategy.js';
import { writeFileSync, mkdtempSync, rmSync } from 'fs';
import { join } from 'path';
import { tmpdir } from 'os';

test('AnnotationStrategy - classify identifies test by @Test annotation', async () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'test-'));
  const testFile = join(tempDir, 'SomeTest.java');
  writeFileSync(testFile, `
package io.camunda.test;

import org.junit.jupiter.api.Test;

public class SomeTest {
  @Test
  void testSomething() {
  }
}
`);

  try {
    const classifier = new AnnotationStrategy();
    const file = {
      path: 'service/src/test/java/io/camunda/service/SomeTest.java',
      fullPath: testFile
    };

    const result = await classifier.classify(file);

    assert.equal(result.type, 'test');
    assert.equal(result.testType, null); // Annotation doesn't tell us unit vs integration
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test('AnnotationStrategy - classify identifies test by @ParameterizedTest annotation', async () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'test-'));
  const testFile = join(tempDir, 'SomeTest.java');
  writeFileSync(testFile, `
package io.camunda.test;

import org.junit.jupiter.params.ParameterizedTest;

public class SomeTest {
  @ParameterizedTest
  void testSomething(String param) {
  }
}
`);

  try {
    const classifier = new AnnotationStrategy();
    const file = {
      path: 'service/src/test/java/io/camunda/service/SomeTest.java',
      fullPath: testFile
    };

    const result = await classifier.classify(file);

    assert.equal(result.type, 'test');
    assert.equal(result.testType, null);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test('AnnotationStrategy - classify identifies utility by @TestConfiguration', async () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'test-'));
  const testFile = join(tempDir, 'TestConfig.java');
  writeFileSync(testFile, `
package io.camunda.test;

import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestConfig {
}
`);

  try {
    const classifier = new AnnotationStrategy();
    const file = {
      path: 'service/src/test/java/io/camunda/service/TestConfig.java',
      fullPath: testFile
    };

    const result = await classifier.classify(file);

    assert.equal(result.type, 'utility');
    assert.equal(result.testType, null);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test('AnnotationStrategy - classify returns no opinion for file without annotation', async () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'test-'));
  const testFile = join(tempDir, 'SomeClass.java');
  writeFileSync(testFile, `
package io.camunda.test;

public class SomeClass {
  void someMethod() {
  }
}
`);

  try {
    const classifier = new AnnotationStrategy();
    const file = {
      path: 'service/src/test/java/io/camunda/service/SomeClass.java',
      fullPath: testFile
    };

    const result = await classifier.classify(file);

    assert.equal(result.type, null);
    assert.equal(result.testType, null);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test('AnnotationStrategy - classify ignores annotations in comments', async () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'test-'));
  const testFile = join(tempDir, 'SomeClass.java');
  writeFileSync(testFile, `
package io.camunda.test;

public class SomeClass {
  // This is not a test: @Test
  /* Another comment with @Test */
  void someMethod() {
  }
}
`);

  try {
    const classifier = new AnnotationStrategy();
    const file = {
      path: 'service/src/test/java/io/camunda/service/SomeClass.java',
      fullPath: testFile
    };

    const result = await classifier.classify(file);

    assert.equal(result.type, null);
    assert.equal(result.testType, null);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test('AnnotationStrategy - getName returns correct name', () => {
  const classifier = new AnnotationStrategy();
  assert.equal(classifier.getName(), 'annotation');
});
