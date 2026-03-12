/**
 * Tests for Codeowners Utility
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { isCodeownersCliAvailable, getCodeowners, getBatchCodeowners } from './codeowners-util.js';
import { writeFileSync, mkdtempSync, mkdirSync, rmSync } from 'fs';
import { join } from 'path';
import { tmpdir } from 'os';

test('isCodeownersCliAvailable - returns a boolean', () => {
  // Test that the function returns a boolean value without assuming its state
  // The actual value depends on whether codeowners-cli is installed in the environment
  const isAvailable = isCodeownersCliAvailable();
  assert.equal(typeof isAvailable, 'boolean');
});

test('getCodeowners - throws error when no .codeowners file is found', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));

  try {
    assert.throws(
      () => getCodeowners('test.java', tempRepo),
      /No \.codeowners or CODEOWNERS file found/
    );
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getCodeowners - returns (unowned) when codeowners-cli is not available', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  writeFileSync(join(tempRepo, '.codeowners'), '# Empty codeowners');

  try {
    const owners = getCodeowners('test.java', tempRepo);
    assert.deepEqual(owners, ['(unowned)']);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getCodeowners - finds .codeowners file in parent directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const subDir = join(tempRepo, 'service', 'src', 'test');

  // Create subdirectory structure
  mkdirSync(subDir, { recursive: true });

  // Create .codeowners at repo root
  writeFileSync(join(tempRepo, '.codeowners'), '# Empty codeowners');

  try {
    // Should find .codeowners by traversing up from subDir
    const owners = getCodeowners('test.java', subDir);
    assert.deepEqual(owners, ['(unowned)']);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getCodeowners - supports CODEOWNERS (uppercase) filename', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  writeFileSync(join(tempRepo, 'CODEOWNERS'), '# Empty codeowners');

  try {
    const owners = getCodeowners('test.java', tempRepo);
    assert.deepEqual(owners, ['(unowned)']);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getBatchCodeowners - throws error when no .codeowners file is found', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));

  try {
    assert.throws(
      () => getBatchCodeowners(['test.java'], tempRepo),
      /No \.codeowners or CODEOWNERS file found/
    );
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getBatchCodeowners - returns (unowned) for all files when codeowners-cli is not available', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  writeFileSync(join(tempRepo, '.codeowners'), '# Empty codeowners');

  try {
    const paths = ['test1.java', 'test2.java', 'test3.java'];
    const ownersMap = getBatchCodeowners(paths, tempRepo);

    assert.equal(ownersMap.size, 3);
    assert.deepEqual(ownersMap.get('test1.java'), ['(unowned)']);
    assert.deepEqual(ownersMap.get('test2.java'), ['(unowned)']);
    assert.deepEqual(ownersMap.get('test3.java'), ['(unowned)']);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getBatchCodeowners - handles empty array', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  writeFileSync(join(tempRepo, '.codeowners'), '# Empty codeowners');

  try {
    const ownersMap = getBatchCodeowners([], tempRepo);
    assert.equal(ownersMap.size, 0);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('getBatchCodeowners - finds .codeowners file in parent directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const subDir = join(tempRepo, 'service', 'src', 'test');

  // Create subdirectory structure
  mkdirSync(subDir, { recursive: true });

  // Create .codeowners at repo root
  writeFileSync(join(tempRepo, '.codeowners'), '# Empty codeowners');

  try {
    // Should find .codeowners by traversing up from subDir
    const paths = ['test.java'];
    const ownersMap = getBatchCodeowners(paths, subDir);

    assert.equal(ownersMap.size, 1);
    assert.deepEqual(ownersMap.get('test.java'), ['(unowned)']);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});
