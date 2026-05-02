'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const FIXTURE = 'semantic-establishes';
const SHAPE_RULE = 'semantic-establishes-shape';
const REQUIRES_SHAPE_RULE = 'semantic-requires-shape';
const REGISTRY_RULE = 'verify-semantic-kinds-registered';

describe('verifySemanticKindsRegistered + schema rules', () => {
  let allResults;
  let shapeViolations;
  let requiresShapeViolations;
  let registryViolations;

  before(() => {
    allResults = lintFixture(FIXTURE);
    shapeViolations = filterByRule(allResults, SHAPE_RULE);
    requiresShapeViolations = filterByRule(allResults, REQUIRES_SHAPE_RULE);
    registryViolations = filterByRule(allResults, REGISTRY_RULE);
  });

  // ── Valid cases ──────────────────────────────────────────────

  describe('valid: well-formed annotations matched to registry', () => {
    it('produces no violations for createWidget (single-key entity establish)', () => {
      const v = allResults.filter((e) => e.message.includes('createWidget'));
      assert.equal(v.length, 0);
    });

    it('produces no violations for getWidget (matching consumer)', () => {
      const v = allResults.filter(
        (e) =>
          e.message.includes('getWidget') &&
          (e.code === SHAPE_RULE ||
            e.code === REQUIRES_SHAPE_RULE ||
            e.code === REGISTRY_RULE),
      );
      assert.equal(v.length, 0);
    });

    it('produces no violations for assignOwnerToWidget (edge establish)', () => {
      const v = allResults.filter(
        (e) =>
          e.message.includes('assignOwnerToWidget') &&
          (e.code === SHAPE_RULE ||
            e.code === REQUIRES_SHAPE_RULE ||
            e.code === REGISTRY_RULE),
      );
      assert.equal(v.length, 0);
    });

    it('produces no violations for searchOwnersForWidget (edge consumer binding partial tuple)', () => {
      const v = allResults.filter(
        (e) =>
          e.message.includes('searchOwnersForWidget') &&
          (e.code === SHAPE_RULE ||
            e.code === REQUIRES_SHAPE_RULE ||
            e.code === REGISTRY_RULE),
      );
      assert.equal(v.length, 0);
    });
  });

  // ── Shape lint: x-semantic-establishes ───────────────────────

  describe('semantic-establishes-shape', () => {
    function violationsForPath(seg) {
      return shapeViolations.filter(
        (v) => Array.isArray(v.path) && v.path.includes(seg),
      );
    }

    it('flags missing required `kind`', () => {
      assert.ok(violationsForPath('/invalid/missing-kind').length >= 1);
    });

    it('flags `kind` violating PascalCase pattern', () => {
      assert.ok(violationsForPath('/invalid/bad-kind-case').length >= 1);
    });

    it('flags typo on a top-level key (additionalProperties: false)', () => {
      assert.ok(violationsForPath('/invalid/typoed-key').length >= 1);
    });

    it('flags bad `shape` enum value', () => {
      assert.ok(violationsForPath('/invalid/bad-shape-enum').length >= 1);
    });

    it('flags empty `identifiedBy` array', () => {
      assert.ok(violationsForPath('/invalid/empty-identified-by').length >= 1);
    });

    it('flags identifiedBy item with bad `in` enum value', () => {
      assert.ok(
        violationsForPath('/invalid/identified-by-bad-in').length >= 1,
      );
    });
  });

  // ── Shape lint: x-semantic-requires ──────────────────────────

  describe('semantic-requires-shape', () => {
    it('flags missing required `bind`', () => {
      const v = requiresShapeViolations.filter(
        (e) => Array.isArray(e.path) && e.path.includes('/invalid/requires-missing-bind'),
      );
      assert.ok(v.length >= 1);
    });
  });

  // ── Registry / cross-reference ───────────────────────────────

  describe('verify-semantic-kinds-registered', () => {
    it('flags an `establishes` kind not in the registry', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Unknown semantic kind 'Sprocket'"),
      );
      assert.equal(v.length, 1);
      assert.match(v[0].message, /x-semantic-establishes/);
    });

    it('flags a `requires` kind not in the registry', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Unknown semantic kind 'Gizmo'"),
      );
      assert.equal(v.length, 1);
      assert.match(v[0].message, /x-semantic-requires/);
    });

    it('flags a `requires` kind that is registered but never established', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'OrphanedKind'"),
      );
      assert.equal(v.length, 1);
      assert.match(v[0].message, /no operation establishes it/);
    });

    it('does not double-report an unknown kind as an orphan', () => {
      // Sprocket and Gizmo are unknown — they get the registry message, not
      // the orphan message.
      const sprocketOrphan = registryViolations.filter(
        (e) =>
          e.message.includes('Sprocket') &&
          e.message.includes('no operation establishes it'),
      );
      const gizmoOrphan = registryViolations.filter(
        (e) =>
          e.message.includes('Gizmo') &&
          e.message.includes('no operation establishes it'),
      );
      assert.equal(sprocketOrphan.length, 0);
      assert.equal(gizmoOrphan.length, 0);
    });

    it('does not flag the valid Widget consumer (Widget is established by createWidget)', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'Widget'"),
      );
      assert.equal(v.length, 0);
    });

    it('does not flag the valid WidgetOwnership consumer (established by assignOwnerToWidget)', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'WidgetOwnership'"),
      );
      assert.equal(v.length, 0);
    });
  });
});
