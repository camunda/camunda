// Tests for verifyAllFieldsRequired custom Spectral function
// This function enforces that all schema properties are either:
// 1. In the required array, OR
// 2. Explicitly marked as nullable: true

const assert = require('assert');
const verifyAllFieldsRequired = require('../verifyAllFieldsRequired.js');

// Helper to create context with path
function createContext(path) {
  return { path };
}

// Test suite
console.log('Running tests for verifyAllFieldsRequired...\n');

let testsRun = 0;
let testsPassed = 0;

function runTest(name, testFn) {
  testsRun++;
  try {
    testFn();
    testsPassed++;
    console.log(`✓ ${name}`);
  } catch (error) {
    console.log(`✗ ${name}`);
    console.log(`  Error: ${error.message}`);
  }
}

// ============================================================================
// POSITIVE TESTS - Should NOT produce errors
// ============================================================================

runTest('Should pass when all properties are in required array', () => {
  const schema = {
    type: 'object',
    required: ['field1', 'field2', 'field3'],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number' },
      field3: { type: 'boolean' }
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 0, 'Should have no errors');
});

runTest('Should pass when optional field is explicitly nullable', () => {
  const schema = {
    type: 'object',
    required: ['field1', 'field2'],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number' },
      field3: { type: 'string', nullable: true }
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 0, 'Should have no errors');
});

runTest('Should pass when all optional fields are nullable', () => {
  const schema = {
    type: 'object',
    required: ['requiredField'],
    properties: {
      requiredField: { type: 'string' },
      optionalField1: { type: 'string', nullable: true },
      optionalField2: { type: 'number', nullable: true },
      optionalField3: { type: 'boolean', nullable: true }
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 0, 'Should have no errors');
});

runTest('Should pass when schema has no properties', () => {
  const schema = {
    type: 'object',
    required: []
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 0, 'Should have no errors');
});

runTest('Should pass when schema is not an object type', () => {
  const schema = {
    type: 'string'
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 0, 'Should have no errors for non-object schemas');
});

runTest('Should pass for null or undefined input', () => {
  const context = createContext(['components', 'schemas', 'TestSchema']);
  assert.strictEqual(verifyAllFieldsRequired(null, {}, context).length, 0);
  assert.strictEqual(verifyAllFieldsRequired(undefined, {}, context).length, 0);
});

runTest('Should pass when required array is not present but no properties', () => {
  const schema = {
    type: 'object'
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 0, 'Should have no errors');
});

// ============================================================================
// NEGATIVE TESTS - Should produce errors
// ============================================================================

runTest('Should fail when a property is not in required array and not nullable', () => {
  const schema = {
    type: 'object',
    required: ['field1'],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number' }  // Missing from required, not nullable
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 1, 'Should have one error');
  assert.ok(errors[0].message.includes('All fields in this schema must be in the required array'));
  assert.ok(errors[0].message.includes('explicitly mark that field as nullable'));
  assert.deepStrictEqual(errors[0].path, ['components', 'schemas', 'TestSchema', 'properties', 'field2']);
});

runTest('Should fail when multiple properties are not in required array and not nullable', () => {
  const schema = {
    type: 'object',
    required: ['field1'],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number' },  // Missing from required, not nullable
      field3: { type: 'boolean' }  // Missing from required, not nullable
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 2, 'Should have two errors');
});

runTest('Should fail when no fields are in required array', () => {
  const schema = {
    type: 'object',
    required: [],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number' }
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 2, 'Should have two errors');
});

runTest('Should fail when required array is missing and properties exist', () => {
  const schema = {
    type: 'object',
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number' }
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 2, 'Should have two errors');
});

runTest('Should fail when nullable is false (not explicitly true)', () => {
  const schema = {
    type: 'object',
    required: ['field1'],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number', nullable: false }  // nullable: false doesn't count
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 1, 'Should have one error');
});

runTest('Should fail when nullable is a string "true" (not boolean)', () => {
  const schema = {
    type: 'object',
    required: ['field1'],
    properties: {
      field1: { type: 'string' },
      field2: { type: 'number', nullable: "true" }  // string "true" doesn't count
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 1, 'Should have one error');
});

// ============================================================================
// EDGE CASE TESTS
// ============================================================================

runTest('Should handle mixed scenarios correctly', () => {
  const schema = {
    type: 'object',
    required: ['requiredField1', 'requiredField2'],
    properties: {
      requiredField1: { type: 'string' },
      requiredField2: { type: 'number' },
      nullableField: { type: 'string', nullable: true },
      missingField: { type: 'boolean' }  // This should error
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 1, 'Should have one error for missingField');
  assert.deepStrictEqual(errors[0].path, ['components', 'schemas', 'TestSchema', 'properties', 'missingField']);
});

runTest('Should handle complex property types (with allOf, $ref, etc.)', () => {
  const schema = {
    type: 'object',
    required: ['field1'],
    properties: {
      field1: { 
        allOf: [
          { $ref: '#/components/schemas/SomeRef' }
        ]
      },
      field2: {
        type: 'object',
        properties: {
          nested: { type: 'string' }
        }
      }  // Missing from required, not nullable
    }
  };
  const context = createContext(['components', 'schemas', 'TestSchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 1, 'Should have one error for field2');
});

runTest('Should provide correct error path for each missing field', () => {
  const schema = {
    type: 'object',
    required: [],
    properties: {
      alpha: { type: 'string' },
      beta: { type: 'number' },
      gamma: { type: 'boolean' }
    }
  };
  const context = createContext(['components', 'schemas', 'MySchema']);
  const errors = verifyAllFieldsRequired(schema, {}, context);
  assert.strictEqual(errors.length, 3, 'Should have three errors');
  
  // Check that each error has correct path
  const errorPaths = errors.map(e => e.path);
  assert.ok(errorPaths.some(p => p[p.length - 1] === 'alpha'), 'Should have error for alpha');
  assert.ok(errorPaths.some(p => p[p.length - 1] === 'beta'), 'Should have error for beta');
  assert.ok(errorPaths.some(p => p[p.length - 1] === 'gamma'), 'Should have error for gamma');
});

// ============================================================================
// SUMMARY
// ============================================================================

console.log(`\n${'='.repeat(50)}`);
console.log(`Tests run: ${testsRun}`);
console.log(`Tests passed: ${testsPassed}`);
console.log(`Tests failed: ${testsRun - testsPassed}`);
console.log('='.repeat(50));

if (testsPassed === testsRun) {
  console.log('\n✓ All tests passed!');
  process.exit(0);
} else {
  console.log('\n✗ Some tests failed!');
  process.exit(1);
}
