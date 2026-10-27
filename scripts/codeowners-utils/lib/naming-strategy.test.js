/**
 * Tests for Naming Strategy
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { NamingStrategy } from './naming-strategy.js';

test('NamingStrategy - classify identifies unit test by Test suffix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserServiceTest.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserServiceTest.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('NamingStrategy - classify identifies unit test by Tests suffix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserServiceTests.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserServiceTests.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('NamingStrategy - classify identifies unit test by TestCase suffix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserServiceTestCase.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserServiceTestCase.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('NamingStrategy - classify identifies unit test by Test prefix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/TestUserService.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/TestUserService.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('NamingStrategy - classify identifies integration test by IT suffix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserServiceIT.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserServiceIT.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'integration');
});

test('NamingStrategy - classify identifies integration test by ITCase suffix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserServiceITCase.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserServiceITCase.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'integration');
});

test('NamingStrategy - classify identifies integration test by IT prefix', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/ITUserService.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/ITUserService.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'integration');
});

test('NamingStrategy - classify returns no opinion for non-test files', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/UserService.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/UserService.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, null);
  assert.equal(result.testType, null);
});

test('NamingStrategy - classify identifies utility by TestUtil pattern', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/TestUtil.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/TestUtil.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'utility');
  assert.equal(result.testType, null);
});

test('NamingStrategy - classify identifies utility by TestData pattern', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/TestData.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/TestData.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'utility');
  assert.equal(result.testType, null);
});

test('NamingStrategy - classify identifies utility by Test*Controller pattern', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/TestController.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/TestController.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'utility');
  assert.equal(result.testType, null);
});

test('NamingStrategy - classify identifies interface as utility', async () => {
  const classifier = new NamingStrategy();
  const file = {
    path: 'service/src/test/java/io/camunda/service/TestInterface.java',
    fullPath: '/repo/service/src/test/java/io/camunda/service/TestInterface.java'
  };

  const result = await classifier.classify(file);

  assert.equal(result.type, 'utility');
  assert.equal(result.testType, null);
});

test('NamingStrategy - getName returns correct name', () => {
  const classifier = new NamingStrategy();
  assert.equal(classifier.getName(), 'naming');
});
