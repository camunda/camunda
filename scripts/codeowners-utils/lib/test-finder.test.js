/**
 * Tests for Test File Finder
 */

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { findTestFiles } from './test-finder.js';
import { writeFileSync, mkdtempSync, mkdirSync, rmSync } from 'fs';
import { join } from 'path';
import { tmpdir } from 'os';

test('TestFinder - finds Java files in test directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  mkdirSync(testDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(testDir, 'AccountServiceTest.java'), 'public class AccountServiceTest {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 2);
    assert.equal(warnings.length, 0);

    const paths = testFiles.map(f => f.path);
    assert.ok(paths.some(p => p.includes('UserServiceTest.java')));
    assert.ok(paths.some(p => p.includes('AccountServiceTest.java')));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - skips node_modules directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  const nodeModulesTestDir = join(tempRepo, 'node_modules', 'some-package', 'src', 'test', 'java');
  mkdirSync(testDir, { recursive: true });
  mkdirSync(nodeModulesTestDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(nodeModulesTestDir, 'ShouldBeSkipped.java'), 'public class ShouldBeSkipped {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 1);
    assert.equal(warnings.length, 0);
    assert.ok(testFiles[0].path.includes('UserServiceTest.java'));
    assert.ok(!testFiles[0].path.includes('node_modules'));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - skips target directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  const targetTestDir = join(tempRepo, 'target', 'src', 'test', 'java');
  mkdirSync(testDir, { recursive: true });
  mkdirSync(targetTestDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(targetTestDir, 'ShouldBeSkipped.java'), 'public class ShouldBeSkipped {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 1);
    assert.equal(warnings.length, 0);
    assert.ok(testFiles[0].path.includes('UserServiceTest.java'));
    assert.ok(!testFiles[0].path.includes('target'));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - only includes files from src/test/java directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  const mainDir = join(tempRepo, 'service', 'src', 'main', 'java', 'io', 'camunda');
  mkdirSync(testDir, { recursive: true });
  mkdirSync(mainDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(mainDir, 'UserService.java'), 'public class UserService {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 1);
    assert.equal(warnings.length, 0);
    assert.ok(testFiles[0].path.includes('UserServiceTest.java'));
    assert.ok(testFiles[0].path.includes('/src/test/java/'));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - returns empty results for empty repository', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 0);
    assert.equal(warnings.length, 0);
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - includes fullPath and relative path', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  mkdirSync(testDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 1);
    assert.equal(warnings.length, 0);

    const testFile = testFiles[0];
    assert.ok(testFile.path); // relative path
    assert.ok(testFile.fullPath); // absolute path
    assert.ok(testFile.fullPath.startsWith(tempRepo));
    assert.ok(testFile.path.includes('service/src/test/java/io/camunda/UserServiceTest.java'));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - handles multiple modules', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir1 = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  const testDir2 = join(tempRepo, 'gateway', 'src', 'test', 'java', 'io', 'camunda');
  mkdirSync(testDir1, { recursive: true });
  mkdirSync(testDir2, { recursive: true });

  writeFileSync(join(testDir1, 'ServiceTest.java'), 'public class ServiceTest {}');
  writeFileSync(join(testDir2, 'GatewayTest.java'), 'public class GatewayTest {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 2);
    assert.equal(warnings.length, 0);

    const paths = testFiles.map(f => f.path);
    assert.ok(paths.some(p => p.includes('service') && p.includes('ServiceTest.java')));
    assert.ok(paths.some(p => p.includes('gateway') && p.includes('GatewayTest.java')));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});

test('TestFinder - skips .git directories', () => {
  const tempRepo = mkdtempSync(join(tmpdir(), 'test-repo-'));
  const testDir = join(tempRepo, 'service', 'src', 'test', 'java', 'io', 'camunda');
  const gitTestDir = join(tempRepo, '.git', 'src', 'test', 'java');
  mkdirSync(testDir, { recursive: true });
  mkdirSync(gitTestDir, { recursive: true });

  writeFileSync(join(testDir, 'UserServiceTest.java'), 'public class UserServiceTest {}');
  writeFileSync(join(gitTestDir, 'ShouldBeSkipped.java'), 'public class ShouldBeSkipped {}');

  try {
    const { testFiles, warnings } = findTestFiles(tempRepo);

    assert.equal(testFiles.length, 1);
    assert.equal(warnings.length, 0);
    assert.ok(testFiles[0].path.includes('UserServiceTest.java'));
    assert.ok(!testFiles[0].path.includes('.git'));
  } finally {
    rmSync(tempRepo, { recursive: true, force: true });
  }
});
