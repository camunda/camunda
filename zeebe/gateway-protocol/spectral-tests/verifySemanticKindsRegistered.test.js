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
      // Scope: orphan / unknown messages only. Other error families (e.g.
      // shape-vs-registry mismatch) legitimately mention 'Widget' on
      // dedicated mismatch fixtures and are exercised by their own tests.
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'Widget'") &&
          (e.message.includes('no operation establishes it') ||
            /^Unknown semantic kind/.test(e.message)),
      );
      assert.equal(v.length, 0);
    });

    it('does not flag the valid WidgetOwnership consumer (established by assignOwnerToWidget)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'WidgetOwnership'") &&
          (e.message.includes('no operation establishes it') ||
            /^Unknown semantic kind/.test(e.message)),
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

  // ── External entities (camunda/camunda#52320) ────────────────

  describe('verify-semantic-kinds-registered: external entities', () => {
    it('does not flag the valid edge whose endpoint resolves to an external entity', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('assignExternalThingToWidget') ||
          e.message.includes('/valid/widgets/{widgetId}/external-things'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    it('does not flag ExternalThing as an orphan derived requires (external entities have no producer)', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'ExternalThing'") &&
        e.message.includes('derived from'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    it('flags a direct x-semantic-establishes against an external entity', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'ExternalThing'") &&
        e.message.includes('cannot be established'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
      assert.match(v[0].message, /external-entity/);
    });

    it('flags a direct x-semantic-requires against an external entity', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'ExternalThing'") &&
        e.message.includes('cannot be required directly'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
      assert.match(v[0].message, /external-entity/);
    });
  });

  // ── Binding existence (camunda/camunda#52413) ────────────────
  // The shape rules guarantee `{in,name,semanticType}` and `{from,name}`
  // are well-formed. The existence check then verifies the named member
  // actually exists on the operation. Class-scoped: one fixture per
  // source locator (path / query / header / body) on each side
  // (establishes / requires) plus the issue's "no requestBody at all"
  // shape, so every category is exercised.
  describe('verify-semantic-kinds-registered: binding existence', () => {
    it('flags establishes binding to body when the operation declares no requestBody', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('establishesBodyNoBody') ||
          e.message.includes('/invalid/establishes-body-no-body'),
      );
      assert.ok(
        v.some((e) => /declares no requestBody/.test(e.message)),
        JSON.stringify(v, null, 2),
      );
    });

    it('flags establishes binding to a body member that no media-type schema declares', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('establishesBodyMissingProp') ||
          e.message.includes('/invalid/establishes-body-missing-prop'),
      );
      assert.ok(
        v.some(
          (e) =>
            /references body member 'widgetId'/.test(e.message) &&
            /no requestBody media-type schema declares/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('flags establishes binding to an undeclared path parameter', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('establishesPathMissing') ||
          e.message.includes('/invalid/establishes-path-missing'),
      );
      assert.ok(
        v.some(
          (e) =>
            /references path parameter 'widgetId'/.test(e.message) &&
            /no such path parameter is declared/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('flags requires binding to an undeclared query parameter', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('requiresQueryMissing') ||
          e.message.includes('/invalid/requires-query-missing'),
      );
      assert.ok(
        v.some(
          (e) =>
            /references query parameter 'widgetId'/.test(e.message) &&
            /no such query parameter is declared/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('flags requires binding to an undeclared header parameter', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('requiresHeaderMissing') ||
          e.message.includes('/invalid/requires-header-missing'),
      );
      assert.ok(
        v.some(
          (e) =>
            /references header parameter 'X-Widget-Id'/.test(e.message) &&
            /no such header parameter is declared/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('flags requires binding to a body member that no media-type schema declares', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('requiresBodyMissingProp') ||
          e.message.includes('/invalid/requires-body-missing-prop'),
      );
      assert.ok(
        v.some(
          (e) =>
            /references body member 'widgetId'/.test(e.message) &&
            /no requestBody media-type schema declares/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('honors path-item-level parameters (does not flag a path binding declared at path-item level)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('getWidgetPathItem') ||
          e.message.includes('/valid/widgets-pathitem'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    it('does not flag the valid happy-path operations', () => {
      const happy = [
        'createWidget',
        'createUser',
        'getWidget',
        'assignOwnerToWidget',
        'searchOwnersForWidget',
      ];
      const v = registryViolations.filter(
        (e) =>
          /references body member|references path parameter|references query parameter|references header parameter/.test(
            e.message,
          ) && happy.some((op) => e.message.includes(op)),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });
  });

  // ── Shape consistency vs registry ─────────────────────────────
  // Class-scoped: covers both directions of the mismatch (edge kind
  // annotated as entity, plus entity kind annotated as edge). The
  // dangerous case is an edge kind silently treated as entity, which
  // bypasses edge-endpoint resolution and the implicit requires it
  // derives — a real coverage gap in #52322 review.
  describe('verify-semantic-kinds-registered: shape vs registry', () => {
    it('flags an edge kind establishes that omits `shape:` (defaults to entity)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('edgeKindShapeDefaulted') ||
          e.message.includes('/invalid/widgets/{widgetId}/wrong-shape-default'),
      );
      assert.ok(
        v.some(
          (e) =>
            /'WidgetOwnership' is registered as 'shape: edge'/.test(e.message) &&
            /declares 'shape: entity' \(default\)/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('flags an entity kind establishes that declares `shape: edge`', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('entityKindShapeEdge') ||
          e.message.includes('/invalid/widgets/wrong-shape-explicit'),
      );
      assert.ok(
        v.some(
          (e) =>
            /'Widget' is registered as 'shape: entity'/.test(e.message) &&
            /declares 'shape: edge'/.test(e.message),
        ),
        JSON.stringify(v, null, 2),
      );
    });

    it('does not flag the valid happy-path establishes (Widget as entity, WidgetOwnership as edge)', () => {
      const v = registryViolations.filter(
        (e) =>
          /is registered as 'shape:/.test(e.message) &&
          (e.message.includes('createWidget') ||
            e.message.includes('createUser') ||
            e.message.includes('assignOwnerToWidget')),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });
  });

  // ── Producer-existence gate ──────────────────────────────────
  // A producer with a broken `establishes` block (unknown kind, illegal
  // external-entity, shape-vs-registry mismatch, broken identifiedBy) is
  // not a usable producer. The gate must keep that kind out of the
  // `established` set so downstream consumers still report the orphan
  // error and the real downstream impact is visible — not silently
  // masked by the local producer error.
  describe('verify-semantic-kinds-registered: producer-existence gate', () => {
    it('reports orphan on the consumer when its only producer has a broken establishes block', () => {
      // brokenEstablishProducer establishes BrokenProducedKind via an
      // undeclared path parameter (a real establishes-side defect).
      // brokenEstablishConsumer requires BrokenProducedKind. Without the
      // gate, the local producer error would mask the consumer orphan.
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'BrokenProducedKind'") &&
          e.message.includes('no operation establishes it') &&
          e.message.includes('/invalid/broken-establish-consumer'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('still reports the local producer error too', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/broken-establish') &&
          /references path parameter 'brokenProducedId'/.test(e.message),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });
  });

  // ── Per-file pass: only the producer-existence cross-reference is gated ──
  // CI lints both the bundled entry (rest-api.yaml) and each domain file
  // independently. The cross-reference walk relies on every producer being
  // visible in `paths`, which is true only for the bundled pass. Without
  // gating, edges in tenants.yaml/roles.yaml/groups.yaml that bind
  // User/MappingRule (whose producers live in users.yaml/
  // mapping-rules.yaml) would falsely report 'no operation establishes
  // ...'. Per-operation checks (unknown kind, illegal direct
  // establishes/requires of an external-entity, edge-endpoint
  // single-owner resolution) must keep firing on fragments — they only
  // need the registry plus the operation in front of them.
  describe('verify-semantic-kinds-registered: fragment behaviour', () => {
    it('does not produce cross-reference errors when linting a well-formed fragment standalone', () => {
      const results = lintFixtureFile(FIXTURE, 'fragment-only.yaml');
      const registry = filterByRule(results, REGISTRY_RULE);
      assert.equal(
        registry.length,
        0,
        `Well-formed fragment lint should produce no verify-semantic-kinds-registered errors. Got: ${JSON.stringify(registry, null, 2)}`,
      );
    });

    describe('per-operation checks still fire on fragments', () => {
      let v;
      before(() => {
        const results = lintFixtureFile(FIXTURE, 'fragment-bad.yaml');
        v = filterByRule(results, REGISTRY_RULE);
      });

      it('flags unknown kind on x-semantic-establishes', () => {
        const m = v.filter(
          (e) =>
            e.message.includes("Unknown semantic kind 'Wigdet'") &&
            e.message.includes('x-semantic-establishes'),
        );
        assert.equal(m.length, 1, JSON.stringify(v, null, 2));
      });

      it('flags unknown kind on x-semantic-requires', () => {
        const m = v.filter(
          (e) =>
            e.message.includes("Unknown semantic kind 'Wigdet'") &&
            e.message.includes('x-semantic-requires'),
        );
        assert.equal(m.length, 1, JSON.stringify(v, null, 2));
      });

      it('flags direct x-semantic-establishes against an external entity', () => {
        const m = v.filter(
          (e) =>
            e.message.includes("'ExternalThing'") &&
            e.message.includes('cannot be established'),
        );
        assert.equal(m.length, 1, JSON.stringify(v, null, 2));
      });

      it('flags direct x-semantic-requires against an external entity', () => {
        const m = v.filter(
          (e) =>
            e.message.includes("'ExternalThing'") &&
            e.message.includes('cannot be required directly'),
        );
        assert.equal(m.length, 1, JSON.stringify(v, null, 2));
      });

      it('flags unresolved edge endpoint semanticType', () => {
        const m = v.filter(
          (e) =>
            e.message.includes("Edge endpoint semanticType 'MysteryId'") &&
            e.message.includes('not declared as an identifier'),
        );
        assert.equal(m.length, 1, JSON.stringify(v, null, 2));
      });
    });
  });
});
