/**
 * Tests for Location Strategy
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { LocationStrategy, classifyByLocation } from './location-strategy.js';

test('LocationStrategy - classifyByLocation identifies acceptance tests', () => {
  const result = classifyByLocation('qa/acceptance-tests/src/test/java/io/camunda/it/SomeIT.java');
  assert.equal(result.type, 'acceptance');
});

test('LocationStrategy - classifyByLocation identifies QA integration tests', () => {
  const result1 = classifyByLocation('qa/integration-tests/src/test/java/io/camunda/test/SomeTest.java');
  assert.equal(result1.type, 'integration');

  const result2 = classifyByLocation('zeebe/qa/update-tests/src/test/java/io/camunda/zeebe/test/SnapshotTest.java');
  assert.equal(result2.type, 'integration');

  const result3 = classifyByLocation('operate/qa/integration-tests/src/test/java/io/camunda/operate/SomeIT.java');
  assert.equal(result3.type, 'integration');

  const result4 = classifyByLocation('tasklist/qa/integration-tests/src/test/java/io/camunda/tasklist/SomeTest.java');
  assert.equal(result4.type, 'integration');
});

test('LocationStrategy - classifyByLocation returns null for standard paths', () => {
  const result = classifyByLocation('service/src/test/java/io/camunda/service/UserServiceTest.java');
  assert.equal(result.type, null);
});

test('LocationStrategy - classifyByLocation does not use filename patterns', () => {
  const result = classifyByLocation('service/src/test/java/io/camunda/service/UserServiceIT.java');
  assert.equal(result.type, null);
});

test('LocationStrategy - classify returns testType opinion for qa/acceptance-tests', async () => {
  const classifier = new LocationStrategy();
  const file = {
    path: 'qa/acceptance-tests/src/test/java/io/camunda/it/SomeIT.java',
    fullPath: '/repo/qa/acceptance-tests/src/test/java/io/camunda/it/SomeIT.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, null); // Doesn't know if test or utility
  assert.equal(result.testType, 'acceptance');
});

test('LocationStrategy - classify returns testType opinion for zeebe/qa', async () => {
  const classifier = new LocationStrategy();
  const file = {
    path: 'zeebe/qa/update-tests/src/test/java/io/camunda/zeebe/test/SnapshotTest.java',
    fullPath: '/repo/zeebe/qa/update-tests/src/test/java/io/camunda/zeebe/test/SnapshotTest.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, null); // Doesn't know if test or utility
  assert.equal(result.testType, 'integration');
});

test('LocationStrategy - classify returns no opinion for standard paths', async () => {
  const classifier = new LocationStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserServiceTest.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserServiceTest.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, null);
  assert.equal(result.testType, null);
});

test('LocationStrategy - getName returns correct name', () => {
  const classifier = new LocationStrategy();
  assert.equal(classifier.getName(), 'location');
});
