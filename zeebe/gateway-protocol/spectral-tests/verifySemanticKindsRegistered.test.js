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

    // Per-tuple bimodal opt-out: identifiedBy.acceptsExternal:true on
    // /valid/widgets/{widgetId}/bimodals/{bimodalId} declares that the
    // Bimodal endpoint accepts either an in-API producer or an
    // externally-minted ID. The cross-reference walk must NOT push the
    // resolved Bimodal kind onto `required` for that tuple, so no
    // orphan-derived-requires error fires for Bimodal even though no
    // operation in the fixture establishes it. Class-scoped: assert the
    // entire suite has zero Bimodal-orphan errors, not just zero from
    // this one path. This is the only fixture that references Bimodal,
    // so a regression that broke the flag would re-introduce the orphan
    // error at this site (and any future bimodal site).
    it('does not flag Bimodal as an orphan derived requires when identifiedBy.acceptsExternal is true (camunda/camunda#52322)', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("Semantic kind 'Bimodal'") &&
        e.message.includes('derived from'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    // Sanity: acceptsExternal does NOT bypass single-owner identifier
    // resolution. If BimodalId were unregistered or owned by multiple
    // entities, the verifier should still flag — proving the flag only
    // gates the producer-existence cross-reference, not the typo /
    // registry-config check that runs before it.
    it('still resolves the Bimodal endpoint identifier (acceptsExternal does not skip single-owner check)', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes("'BimodalId'") &&
        (e.message.includes('not declared as an identifier') ||
          e.message.includes('resolves to multiple entity kinds')),
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

  // ── Binding existence with $ref'd request body ───────────────
  // Production specs use `requestBody.content.*.schema: { $ref: ... }`
  // for every `createX`. The body-member existence check must follow
  // the `$ref` to inspect the referenced component's properties — if
  // Spectral ref resolution ever regressed, the existing inline-body
  // fixtures would still pass while the check silently disabled itself
  // on every real op. One class-scoped fixture pair (present / missing)
  // pins the resolution behaviour.
  describe('verify-semantic-kinds-registered: binding existence ($ref body)', () => {
    it('does not flag establishes when the $ref\u0027d body schema declares the member', () => {
      const v = registryViolations.filter((e) =>
        e.message.includes('/valid/establishes-body-ref-present'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    it('flags establishes when the $ref\u0027d body schema is missing the member', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/establishes-body-ref-missing') &&
          e.message.includes("body member 'widgetId'"),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });
  });

  // ── Producer-existence gate: structural-shape branch ─────────
  // camunda/camunda#52322 review: errors raised by *other* Spectral
  // rules (e.g. `semantic-establishes-shape`) are not visible inside
  // verifySemanticKindsRegistered's local errors[]. Without a
  // duplicated structural check, a malformed-shape producer would
  // satisfy the producer-existence gate and silently mask the orphan
  // error on every consumer of its kind. Class-scoped: covers the three
  // shape-rule failure modes the reviewer named (empty identifiedBy,
  // missing identifiedBy, typo'd top-level key) plus a single consumer
  // that must report orphan exactly once.
  describe('verify-semantic-kinds-registered: producer-existence gate (structural shape)', () => {
    it('reports orphan on the consumer when every producer of the kind has a malformed establishes block', () => {
      // Three malformed producers (#26-#28) all attempt to establish
      // MalformedShapeKind. None should be admitted to `established`,
      // so the consumer (#29) must report orphan once.
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'MalformedShapeKind'") &&
          e.message.includes('no operation establishes it') &&
          e.message.includes('/invalid/malformed-shape-consumer'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('the shape rule still fires on each malformed producer (not suppressed)', () => {
      // Sanity check: the user-facing shape rule should report on each
      // of the three malformed producers. The producer-existence gate
      // does not push duplicate errors — it only refuses to admit the
      // kind into `established`.
      const paths = [
        '/invalid/malformed-empty-identified-by',
        '/invalid/malformed-missing-identified-by',
        '/invalid/malformed-typoed-top-level-key',
      ];
      for (const p of paths) {
        const m = shapeViolations.filter(
          (e) => Array.isArray(e.path) && e.path.includes(p),
        );
        assert.ok(
          m.length >= 1,
          `Expected semantic-establishes-shape to flag ${p}, got: ${JSON.stringify(shapeViolations, null, 2)}`,
        );
      }
    });
  });

  // ── Binding existence: non-object request body ───────────────
  // camunda/camunda#52322 review: a body binding against a scalar or
  // array request body is impossible and must fail. Previously the
  // walker returned `null` for both unresolved $refs and resolved
  // non-object schemas, so the binding-existence check silently skipped
  // both cases. Tri-state walker {unresolved | walked | non-object}
  // distinguishes them — unresolved still skips (per-file pass with
  // cross-file $ref), non-object now fails. Class-scoped across both
  // establish and require branches and across scalar and array body
  // shapes.
  describe('verify-semantic-kinds-registered: binding existence (non-object body)', () => {
    it('flags establishes when the requestBody schema is a scalar (type: string)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/establishes-body-non-object-scalar') &&
          e.message.includes("body member 'widgetId'") &&
          e.message.includes('non-object type'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('flags requires when the requestBody schema is an array (type: array)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/requires-body-non-object-array') &&
          e.message.includes("body member 'widgetId'") &&
          e.message.includes('non-object type'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });
  });

  // ── Binding existence: closed-object request body ────────────
  // camunda/camunda#52322 review: an explicit
  // `{type: object, additionalProperties: false}` schema with no
  // `properties` documents zero top-level properties. The walker
  // previously returned `unresolved` for this shape (no walk set
  // `walked`, the non-object branch only fires for non-object `type:`
  // strings), so a body binding silently lint-cleaned ("defer to the
  // bundled pass") instead of failing. Fixed by treating explicit
  // `type: object` with no walked branches as `walked` with an empty
  // Set.
  describe('verify-semantic-kinds-registered: binding existence (closed-object body)', () => {
    it('flags establishes when the requestBody schema is `{type: object, additionalProperties: false}` with no properties', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/establishes-body-closed-object') &&
          e.message.includes("body member 'widgetId'") &&
          /no requestBody media-type schema declares/.test(e.message),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });
  });

  // ── Bind-key tuple validation ─────────────────────────────────
  // camunda/camunda#52322 review: each `x-semantic-requires.bind` key
  // on an entity-kind consumer must equal `camelCase(identifier)` for
  // one of the kind's registered identifiers, or appear in the kind's
  // explicit `legacyBindKeys` escape hatch. The existing existence
  // check only validates `binding.name` against the operation's
  // members, so a typo'd bind key like `wigetId` for `kind: Widget`
  // would otherwise lint clean. Class-scoped: covers both the typo
  // failure mode and the legacyBindKeys grandfathered-pass mode.
  describe('verify-semantic-kinds-registered: bind-key tuple validation', () => {
    it('flags a bind key that is not camelCase(identifier) and not in legacyBindKeys', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/requires-bind-bogus-key') &&
          e.message.includes("bind has key 'wigetId'") &&
          e.message.includes("kind 'Widget'"),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('does not flag a bind key listed in the kind\u0027s legacyBindKeys (grandfathered wire field name)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/valid/legacy-bind-consumer') &&
          /bind has key 'id'/.test(e.message),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    it('does not flag valid happy-path bind keys (widgetId on Widget, username on User)', () => {
      const v = registryViolations.filter(
        (e) =>
          /bind has key/.test(e.message) &&
          (e.message.includes('createWidget') ||
            e.message.includes('getWidget') ||
            e.message.includes('createUser') ||
            e.message.includes('searchOwnersForWidget')),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });
  });

  // ── Entity-tuple semanticType validation ──────────────────────
  // camunda/camunda#52322 review: an entity producer's
  // `identifiedBy[].semanticType` must be a registered identifier of
  // SOME entity kind. Without the check, a producer can claim to
  // establish kind X via a typo or stale identifier name and the
  // orphan check on every consumer of X is silently satisfied.
  // Composite-key entities (e.g. TenantClusterVariable) legitimately
  // reference foreign-but-registered identifiers, so the check accepts
  // any registered identifier rather than restricting to the kind's
  // own identifiers — class-scoped fixture covers both the bug class
  // (unregistered semanticType) and the consumer-side orphan to prove
  // the producer-existence gate rejected the malformed producer.
  describe('verify-semantic-kinds-registered: entity-tuple semanticType validation', () => {
    it('flags an entity producer whose identifiedBy.semanticType is not registered as an identifier of any kind', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/establishes-bogus-semantic-type') &&
          e.message.includes("'WrongIdentifierType'") &&
          e.message.includes('not declared as an identifier of any registered entity kind'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('reports orphan on the IsolatedKind consumer (proving the wrong-semanticType producer was not silently admitted)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes("Semantic kind 'IsolatedKind'") &&
          e.message.includes('no operation establishes it') &&
          e.message.includes('/invalid/isolated-kind-consumer'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('does not flag the valid happy-path entity producers (createWidget, createUser, legacyBindProducer)', () => {
      const v = registryViolations.filter(
        (e) =>
          /is not declared as an identifier of any registered entity kind/.test(e.message) &&
          (e.message.includes('createWidget') ||
            e.message.includes('createUser') ||
            e.message.includes('legacyBindProducer')),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });
  });

  // ── Hole D: foreign-identifier-without-requires ──
  // camunda/camunda#52322 review: a composite-key entity producer that
  // lists a foreign-owned identifier (an identifier registered to a
  // different entity kind) in `identifiedBy` must declare a corresponding
  // `x-semantic-requires` on the owning kind. Otherwise the runtime
  // dependency is unannotated and downstream chain planners walk the
  // producer as a root, synthesising an identifier value the engine
  // rejects with NOT_FOUND.
  //
  // Exempt cases (must NOT fire):
  //   - Edge producers — `requires` derived from `identifiedBy` by design.
  //   - Multi-owner sibling pattern — identifier appears on both the
  //     establishing kind's own `identifiers` and a sibling's (e.g.
  //     ClusterVariableName, owned by both Global and Tenant variants).
  //   - Foreign owner is `external-entity` — referenced via edges only,
  //     never required directly.
  describe('verify-semantic-kinds-registered: foreign-identifier-without-requires', () => {
    it('flags an entity producer whose identifiedBy borrows a foreign-owned identifier without a corresponding x-semantic-requires', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/invalid/establishes-foreign-id-no-requires') &&
          e.message.includes("'WidgetId'") &&
          e.message.includes('foreign entity kind') &&
          e.message.includes('[Widget]'),
      );
      assert.equal(v.length, 1, JSON.stringify(registryViolations, null, 2));
    });

    it('does not flag the same shape when the producer also declares the corresponding x-semantic-requires', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('/valid/establishes-foreign-id-with-requires') &&
          e.message.includes('foreign entity kind'),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
    });

    it('does not flag any edge producer (requires are derived from identifiedBy by design)', () => {
      const v = registryViolations.filter(
        (e) =>
          e.message.includes('foreign entity kind') &&
          (e.message.includes('assignWidgetOwner') ||
            e.message.includes('assignWidgetFrobLink') ||
            e.message.includes('assignWidgetExternalLink') ||
            e.message.includes('assignWidgetBimodalLink')),
      );
      assert.equal(v.length, 0, JSON.stringify(v, null, 2));
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
