/**
 * Tests for CombinedStrategy
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { CombinedStrategy } from './combined-strategy.js';
import { TestClassifierStrategy } from './classifier-strategy.js';

// Mock strategy that returns specific opinions
class MockStrategy extends TestClassifierStrategy {
  constructor(name, opinion) {
    super();
    this._name = name;
    this._opinion = opinion;
  }

  getName() {
    return this._name;
  }

  async classify() {
    return this._opinion;
  }
}

test('CombinedStrategy - combines opinions from multiple strategies', async () => {
  const strategy1 = new MockStrategy('strategy1', { type: 'test', testType: null });
  const strategy2 = new MockStrategy('strategy2', { type: null, testType: 'unit' });

  const combined = new CombinedStrategy([strategy1, strategy2]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('CombinedStrategy - later strategies override earlier ones', async () => {
  const strategy1 = new MockStrategy('strategy1', { type: 'test', testType: 'unit' });
  const strategy2 = new MockStrategy('strategy2', { type: 'utility', testType: null });

  const combined = new CombinedStrategy([strategy1, strategy2]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  // strategy2's type should override strategy1's type
  assert.equal(result.type, 'utility');
  // strategy1's testType should be preserved (strategy2 returns null)
  assert.equal(result.testType, 'unit');
});

test('CombinedStrategy - handles all null opinions', async () => {
  const strategy1 = new MockStrategy('strategy1', { type: null, testType: null });
  const strategy2 = new MockStrategy('strategy2', { type: null, testType: null });

  const combined = new CombinedStrategy([strategy1, strategy2]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  assert.equal(result.type, null);
  assert.equal(result.testType, null);
});

test('CombinedStrategy - uses last non-null value for type', async () => {
  const strategy1 = new MockStrategy('strategy1', { type: 'test', testType: null });
  const strategy2 = new MockStrategy('strategy2', { type: null, testType: null });
  const strategy3 = new MockStrategy('strategy3', { type: 'utility', testType: null });

  const combined = new CombinedStrategy([strategy1, strategy2, strategy3]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  assert.equal(result.type, 'utility');
});

test('CombinedStrategy - uses last non-null value for testType', async () => {
  const strategy1 = new MockStrategy('strategy1', { type: null, testType: 'unit' });
  const strategy2 = new MockStrategy('strategy2', { type: null, testType: null });
  const strategy3 = new MockStrategy('strategy3', { type: null, testType: 'integration' });

  const combined = new CombinedStrategy([strategy1, strategy2, strategy3]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  assert.equal(result.testType, 'integration');
});

test('CombinedStrategy - handles empty strategy list', async () => {
  const combined = new CombinedStrategy([]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  assert.equal(result.type, null);
  assert.equal(result.testType, null);
});

test('CombinedStrategy - handles single strategy', async () => {
  const strategy = new MockStrategy('strategy1', { type: 'test', testType: 'unit' });

  const combined = new CombinedStrategy([strategy]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('CombinedStrategy - continues on strategy error', async () => {
  class ErrorStrategy extends TestClassifierStrategy {
    getName() {
      return 'error';
    }

    async classify() {
      throw new Error('Strategy error');
    }
  }

  const strategy1 = new ErrorStrategy();
  const strategy2 = new MockStrategy('strategy2', { type: 'test', testType: 'unit' });

  const combined = new CombinedStrategy([strategy1, strategy2]);
  const result = await combined.classify({ path: 'test.java', fullPath: '/tmp/test.java' });

  // Should get result from strategy2 despite strategy1 error
  assert.equal(result.type, 'test');
  assert.equal(result.testType, 'unit');
});

test('CombinedStrategy - getName returns combined', async () => {
  const combined = new CombinedStrategy([]);
  assert.equal(combined.getName(), 'combined');
});
