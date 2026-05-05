'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, lintFixtureFile, filterByRule } = require('./helpers');

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

    it('does not flag the valid edge endpoints Widget and User (both established in fixture)', () => {
      // assignOwnerToWidget's identifiedBy contains WidgetId (→ Widget) and
      // Username (→ User). Both entities are established by createWidget /
      // createUser, so the derived-requires check must not fire on the
      // valid edge.
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('assignOwnerToWidget') ||
          e.message.includes('/valid/widgets/{widgetId}/owners/{username}'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });
  });

  // ── Edge endpoint resolution + derived requires ──────────────

  describe('verify-semantic-kinds-registered: edge derived requires', () => {
    it('flags an edge identifiedBy.semanticType that no entity owns', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Edge endpoint semanticType 'MysteryId'"),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
      assert.match(v[0].message, /assignMysteryToWidget|\/invalid\/widgets\/\{widgetId\}\/mystery/);
    });

    it('flags a derived requires kind that is registered but never established', () => {
      // assignFrobToWidget's identifiedBy includes FrobId, the registry
      // declares Frob.identifiers = ["FrobId"], but nothing in the fixture
      // establishes Frob.
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'Frob'") &&
          e.message.includes('derived from x-semantic-establishes.identifiedBy'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
      assert.match(v[0].message, /no operation establishes it/);
      assert.match(v[0].message, /FrobId/);
    });

    it('does not flag a resolved-but-established endpoint as orphan (regression: WidgetId resolves to Widget which createWidget establishes)', () => {
      // Both invalid edge fixtures have WidgetId in their identifiedBy.
      // Widget IS established → no orphan errors for Widget.
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'Widget'") &&
          e.message.includes('derived from'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });
  });
});

// ── Per-file lint pass: cross-reference must be skipped on sub-files ──
//
// CI runs spectral twice (see camunda/camunda#46274) — once on the bundled
// rest-api.yaml root and once with a per-file glob over each domain YAML.
// Sub-files like tenants.yaml only carry their own paths, so an edge whose
// producer lives in a sibling file (e.g. assignUserToTenant deriving User
// from Username, with createUser in users.yaml) used to false-positive
// here. The verifier now gates the cross-reference walk on the document
// having `openapi:` at root — sub-files don't, so the check is suppressed.
// The per-operation registry-membership checks above still fire correctly
// per-file.
//
// Regression for camunda/camunda#52308 (PR check failure on
// "Semantic kind 'User' is required ... but no operation establishes it"
// when linting tenants.yaml in isolation).

describe('verifySemanticKindsRegistered: per-file lint pass on sub-files', () => {
  let subfileResults;
  let subfileRegistryViolations;

  before(() => {
    subfileResults = lintFixtureFile(FIXTURE, 'subfile-no-openapi.yaml');
    subfileRegistryViolations = filterByRule(subfileResults, REGISTRY_RULE);
  });

  it('does not raise a cross-reference orphan for an entity established only in a sibling file', () => {
    // The sub-file's edge resolves to Widget (via WidgetId) and User (via
    // Username). Both producers (createWidget, createUser) live in the
    // sibling widgets.yaml. Per-file linting must not flag either as
    // orphaned — that's the bundled-pass's job.
    const orphans = subfileRegistryViolations.filter((e) =>
      e.message.includes('no operation establishes it'),
    );
    assert.equal(
      orphans.length,
      0,
      JSON.stringify(subfileRegistryViolations, null, 2),
    );
  });
});
