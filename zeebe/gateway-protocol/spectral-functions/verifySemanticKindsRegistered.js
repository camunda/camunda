// Spectral custom function. Three responsibilities:
//
//   1. Every `kind:` value referenced by `x-semantic-establishes` or
//      `x-semantic-requires` must appear in `semantic-kinds.json`. Catches
//      typos at the point of declaration (the registry is the spell-check
//      dictionary).
//
//   2. Cross-reference: every `x-semantic-requires.kind` must be established
//      by at least one operation in the spec. Catches the case where the
//      consumer was annotated but the producer wasn't (or producer uses a
//      different — possibly typo'd — kind).
//
//   3. Edge endpoint resolution + derived requires. For every operation with
//      `x-semantic-establishes.shape: edge`, each `identifiedBy[].semanticType`
//      must be the identifier of exactly one registered entity kind (the
//      registry's `identifiers: [...]` field). The resolved entity kinds are
//      then treated as implicit `x-semantic-requires` for that operation and
//      fed through the cross-reference check above. Authors get the
//      validation ("you can't link to an entity nobody can produce") without
//      having to redundantly write `x-semantic-requires` next to every edge
//      establishment.
//
//   Special case for `shape: external-entity` registry entries (camunda/camunda#52320):
//   the producer-existence check is skipped, and any operation that tries
//   to `establishes` or `requires` an external entity directly is rejected
//   — external entities are *referenced* through edge `identifiedBy`
//   tuples only.
//
//   Per-tuple opt-out for bimodal entity sources (camunda/camunda#52322
//   review): an `identifiedBy` entry may set `acceptsExternal: true` to
//   declare that this *specific* edge endpoint accepts either an in-API
//   producer (the canonical local entity) OR an externally-minted ID. The
//   single-owner identifier resolution still runs (typo / config check),
//   but the resolved entity kind is NOT pushed onto `required` for that
//   tuple — so the producer-existence cross-reference is satisfied
//   regardless of whether a producer exists in this API. This is distinct
//   from kind-level `shape: external-entity`: the kind is still locally
//   producible at most other sites; only this tuple is bimodal. Used for
//   `assignGroupToRole`/`assignGroupToTenant`, where BYOG/OIDC
//   deployments accept IdP-supplied group IDs as members of roles or
//   tenants even though group CRUD itself remains local.
//
// The orphan check in the other direction (`establishes` with no consumer)
// is intentionally not enforced — many producers are legitimately useful
// without a consumer in this spec (e.g. setup/admin operations).
//
// Applied to the document root (`given: $`).
//
// The registry is JSON (not YAML) so it can be read with the built-in
// JSON parser without taking on a runtime dependency on js-yaml.
//
// Tests override the registry path via the
// `SPECTRAL_SEMANTIC_KINDS_REGISTRY` environment variable.

'use strict';

const fs = require('node:fs');
const path = require('node:path');

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete']);
const PARAM_LOCATIONS = new Set(['path', 'query', 'header']);

// Allowed top-level keys on `x-semantic-establishes`. Mirrors the
// `semantic-establishes-shape` Spectral rule's `additionalProperties:
// false`. Duplicated here (rather than imported from the rule) because
// this function runs as the producer-existence gate: see
// isWellFormedEstablishes below for the rationale.
const ESTABLISHES_ALLOWED_KEYS = new Set(['kind', 'shape', 'identifiedBy']);
const ESTABLISHES_ITEM_ALLOWED_KEYS = new Set([
  'in',
  'name',
  'semanticType',
  'acceptsExternal',
]);
const ESTABLISHES_SHAPE_VALUES = new Set(['entity', 'edge']);
const ESTABLISHES_LOCATIONS = new Set(['body', 'path', 'query', 'header']);

// Returns true iff `est` matches the structural shape required by the
// `semantic-establishes-shape` Spectral rule. Used to gate
// `established.add(est.kind)` independently of whether the shape rule's
// own errors are visible to this function.
//
// Why duplicate the shape rule here: Spectral's `errors[]` array inside
// a custom function is local — errors emitted by *other* rules (such as
// `semantic-establishes-shape`) are not counted, so a malformed
// establishes block can fail that rule and still be admitted as a
// producer here. That suppresses the orphan-on-consumer error this
// function exists to surface. Class-scoped guard against the defect
// reported on PR #52322.
function isWellFormedEstablishes(est) {
  if (!est || typeof est !== 'object' || Array.isArray(est)) return false;
  if (typeof est.kind !== 'string' || est.kind.length === 0) return false;
  if (!Array.isArray(est.identifiedBy) || est.identifiedBy.length === 0) return false;
  for (const key of Object.keys(est)) {
    if (!ESTABLISHES_ALLOWED_KEYS.has(key)) return false;
  }
  if ('shape' in est) {
    if (typeof est.shape !== 'string' || !ESTABLISHES_SHAPE_VALUES.has(est.shape)) {
      return false;
    }
  }
  for (const item of est.identifiedBy) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return false;
    if (typeof item.in !== 'string' || !ESTABLISHES_LOCATIONS.has(item.in)) return false;
    if (typeof item.name !== 'string' || item.name.length === 0) return false;
    if (typeof item.semanticType !== 'string' || item.semanticType.length === 0) return false;
    for (const key of Object.keys(item)) {
      if (!ESTABLISHES_ITEM_ALLOWED_KEYS.has(key)) return false;
    }
    if ('acceptsExternal' in item && typeof item.acceptsExternal !== 'boolean') {
      return false;
    }
  }
  return true;
}

// Walks a request-body schema and collects the names of top-level
// properties across composition keywords (allOf / oneOf / anyOf). The
// union semantics for oneOf/anyOf are deliberately permissive: a
// binding is accepted if any branch declares the property, because the
// alternative would require the lint to know which branch the binding
// targets — information that is not present in the annotation.
//
// Returns a tri-state result so callers can distinguish:
//   { kind: 'unresolved' } — schema missing or unresolvable in this
//                            pass (per-file pass with cross-file $ref).
//                            Callers skip the existence check; the
//                            bundled pass will catch real violations.
//   { kind: 'walked', props: Set } — at least one composition branch
//                                    walked successfully. Membership
//                                    check applies.
//   { kind: 'non-object' } — schema is present and resolved but is not
//                            an object/composition type (e.g. scalar or
//                            array). Any { in: body, name: ... }
//                            binding against such a body is impossible
//                            and must fail.
function collectTopLevelBodyProperties(schema, seen) {
  if (!schema || typeof schema !== 'object') return { kind: 'unresolved' };
  if (seen.has(schema)) return { kind: 'walked', props: new Set() };
  seen.add(schema);
  const props = new Set();
  let walked = false;
  if (schema.properties && typeof schema.properties === 'object') {
    walked = true;
    for (const k of Object.keys(schema.properties)) props.add(k);
  }
  for (const key of ['allOf', 'oneOf', 'anyOf']) {
    if (!Array.isArray(schema[key])) continue;
    for (const sub of schema[key]) {
      const subRes = collectTopLevelBodyProperties(sub, seen);
      if (subRes.kind === 'unresolved') continue;
      // A non-object branch under oneOf/anyOf does not poison the
      // union — other branches may legitimately be objects. Only treat
      // the body as non-object when the *root* schema has no properties
      // and no composition branches walked (handled below).
      if (subRes.kind === 'non-object') continue;
      walked = true;
      for (const k of subRes.props) props.add(k);
    }
  }
  if (walked) return { kind: 'walked', props };
  // Schema was resolved (object) but declared neither `properties` nor
  // a composition keyword we could walk. Three sub-cases:
  //   - explicit `type: 'object'` with no properties (e.g. closed schema
  //     `{type: object, additionalProperties: false}`) → fully resolved
  //     as an object with zero top-level properties; body bindings are
  //     impossible. Returning `walked` with an empty Set surfaces the
  //     deterministic `no requestBody media-type schema declares ...`
  //     error rather than silently deferring to the bundled pass
  //     (camunda/camunda#52322 review).
  //   - explicit non-object `type` (string/number/integer/boolean/array)
  //     → resolved as non-object; body bindings are impossible.
  //   - everything else (e.g. unresolved $ref node, or an empty object
  //     with no `type` declared) → unresolved, defer to the bundled pass.
  if (typeof schema.type === 'string' && schema.type.length > 0) {
    if (schema.type === 'object') {
      return { kind: 'walked', props: new Set() };
    }
    return { kind: 'non-object' };
  }
  return { kind: 'unresolved' };
}

// Collects the identifiers an annotation may legitimately reference on
// the operation: path/query/header parameters from both the path-item
// and operation level (operation overrides per OpenAPI 3.x), plus the
// top-level body properties of every requestBody media type schema.
//
// Body state is four-valued so callers can distinguish:
//   hasRequestBody=false                       → no requestBody at all
//                                                (hard fail for any body
//                                                binding)
//   hasRequestBody=true, bodyState='unresolved'
//                                              → schema unresolvable in
//                                                this lint pass (silent
//                                                skip — the bundled pass
//                                                will catch it)
//   hasRequestBody=true, bodyState='non-object'
//                                              → all media-type schemas
//                                                resolved to scalar/array
//                                                types. Body bindings are
//                                                impossible (hard fail).
//   hasRequestBody=true, bodyState='walked',
//   bodyProps=Set(...)                         → at least one media-type
//                                                schema walked. Membership
//                                                check applies.
function collectAvailableMembers(pathItem, op) {
  const members = {
    path: new Set(),
    query: new Set(),
    header: new Set(),
    hasRequestBody: false,
    bodyState: 'unresolved',
    bodyProps: null,
  };

  const paramSources = [];
  if (pathItem && Array.isArray(pathItem.parameters)) {
    paramSources.push(...pathItem.parameters);
  }
  if (Array.isArray(op.parameters)) {
    paramSources.push(...op.parameters);
  }
  for (const p of paramSources) {
    if (!p || typeof p !== 'object') continue;
    if (typeof p.name !== 'string' || typeof p.in !== 'string') continue;
    if (!PARAM_LOCATIONS.has(p.in)) continue;
    members[p.in].add(p.name);
  }

  const requestBody = op.requestBody;
  if (requestBody && typeof requestBody === 'object') {
    members.hasRequestBody = true;
    const content = requestBody.content;
    if (content && typeof content === 'object') {
      const bodyProps = new Set();
      let anyWalked = false;
      let anyNonObject = false;
      for (const media of Object.values(content)) {
        const sub = collectTopLevelBodyProperties(media?.schema, new WeakSet());
        if (sub.kind === 'unresolved') continue;
        if (sub.kind === 'non-object') {
          anyNonObject = true;
          continue;
        }
        anyWalked = true;
        for (const k of sub.props) bodyProps.add(k);
      }
      if (anyWalked) {
        members.bodyState = 'walked';
        members.bodyProps = bodyProps;
      } else if (anyNonObject) {
        // Every resolved media-type schema is non-object. Body bindings
        // against this requestBody are impossible — fail any binding
        // attempt. (Mixed object/non-object across media types still
        // resolves as 'walked' above, deliberately permissive.)
        members.bodyState = 'non-object';
      }
      // else: every media-type schema was unresolved; leave 'unresolved'.
    }
  }

  return members;
}

const REGISTRY_CANDIDATES = [
  // CI invokes spectral from the repo root.
  'zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json',
  // Local invocations from inside zeebe/gateway-protocol.
  'src/main/proto/v2/semantic-kinds.json',
];

// Tests override the registry path with this env var so they can drive
// the function with fixture-local kinds without polluting the production
// registry. Spectral's function sandbox doesn't expose __dirname, so we
// can't anchor the lookup to the function file — env var first, then
// cwd-relative candidates.
function resolveRegistryPath() {
  const override = process.env.SPECTRAL_SEMANTIC_KINDS_REGISTRY;
  if (typeof override === 'string' && override.length > 0) {
    return path.resolve(override);
  }
  for (const candidate of REGISTRY_CANDIDATES) {
    const abs = path.resolve(process.cwd(), candidate);
    if (fs.existsSync(abs)) return abs;
  }
  // Last resort — let fs.readFileSync surface a clear ENOENT pointing at
  // the canonical location.
  return path.resolve(process.cwd(), REGISTRY_CANDIDATES[0]);
}

let cachedRegistry = null;
let cachedFor = null;
function loadRegistry() {
  const file = resolveRegistryPath();
  if (cachedRegistry !== null && cachedFor === file) return cachedRegistry;
  const raw = fs.readFileSync(file, 'utf8');
  const doc = JSON.parse(raw);
  const kinds = Array.isArray(doc.kinds) ? doc.kinds : [];
  const names = new Set();
  // Names of kinds whose `shape` is `external-entity`. Such entities are
  // referenced via edge `identifiedBy` tuples only — the cross-reference
  // check skips them, and the operation-walk below rejects any direct
  // `establishes`/`requires` against them.
  const externalNames = new Set();
  // kind name -> registered shape ('entity' | 'edge' | 'external-entity').
  // Used by the shape-vs-registry check: an op annotating an edge kind
  // with `shape: entity` (or omitting `shape`, which defaults to entity)
  // would otherwise bypass edge-endpoint resolution and the implicit
  // requires it derives. The opposite (entity kind annotated as edge) is
  // also flagged to keep the registry the single source of truth.
  const kindShapes = new Map();
  // semanticType (string) -> array of entity kind names that declare it as an
  // identifier. Single-owner is the well-formed case; multi-owner is a
  // registry config error and surfaces as an error against the first edge
  // operation that triggers the lookup.
  const identifierToKinds = new Map();
  // Forward map: kindName -> Set of identifier semanticType strings
  // declared in registry. Powers two checks added in camunda/camunda#52322
  // review:
  //   1. Entity-producer semanticType validation: an entity producer's
  //      `identifiedBy[].semanticType` must be one of the kind's own
  //      registered identifiers. Without this, a producer can claim to
  //      establish kind X via a foreign identifier and the orphan check
  //      on consumers of X is silently satisfied.
  //   2. Bind-key validation: each `x-semantic-requires.bind` key on an
  //      entity-kind consumer must equal `camelCase(identifier)` for one
  //      of the kind's registered identifiers (or a legacy bind key —
  //      see kindLegacyBindKeys below). Catches typos like `rolId` for
  //      `kind: Role`.
  const kindIdentifiers = new Map();
  // kindName -> Set of bind-key strings explicitly grandfathered by the
  // registry. Used when the wire field name doesn't follow the
  // `camelCase(identifier)` convention because the spec name was
  // published before the convention existed (e.g. GlobalTaskListener's
  // path field is `id`, not `globalListenerId`). Adding a new entry
  // here should require the same justification — the wire name is
  // already published and cannot be renamed without breaking SDK users.
  const kindLegacyBindKeys = new Map();
  // kindName -> ordered Array of bind-key strings that the consumer
  // MUST include in `x-semantic-requires.bind` (the kind's full
  // identity tuple). Optional; when absent, the verifier falls back to
  // requiring `camelCase(id)` for each registered identifier of the
  // kind (substitutable via `legacyBindKeys`). Required for composite-
  // key entities where the identity tuple includes a foreign-owned
  // identifier that is intentionally not listed in `identifiers` (e.g.
  // `TenantClusterVariable` whose identity is `(tenantId, name)` but
  // whose `identifiers` contain only `ClusterVariableName`, because
  // `TenantId` must remain single-owner under `Tenant` for the edge-
  // resolution rule). Without this field, a consumer binding only
  // `name` lints clean against the kind's `legacyBindKeys` even though
  // the runtime needs both keys to resolve the entity. PR #52322
  // review.
  const kindRequiredBindKeys = new Map();
  for (const entry of kinds) {
    const name = typeof entry === 'string' ? entry : entry?.name;
    if (typeof name !== 'string' || name.length === 0) continue;
    names.add(name);
    if (entry && typeof entry === 'object') {
      if (typeof entry.shape === 'string') kindShapes.set(name, entry.shape);
      if (entry.shape === 'external-entity') externalNames.add(name);
      if (Array.isArray(entry.identifiers)) {
        const ownIds = new Set();
        for (const id of entry.identifiers) {
          if (typeof id !== 'string' || id.length === 0) continue;
          ownIds.add(id);
          const existing = identifierToKinds.get(id);
          if (existing) existing.push(name);
          else identifierToKinds.set(id, [name]);
        }
        if (ownIds.size > 0) kindIdentifiers.set(name, ownIds);
      }
      if (Array.isArray(entry.legacyBindKeys)) {
        const keys = new Set();
        for (const k of entry.legacyBindKeys) {
          if (typeof k !== 'string' || k.length === 0) continue;
          keys.add(k);
        }
        if (keys.size > 0) kindLegacyBindKeys.set(name, keys);
      }
      if (Array.isArray(entry.requiredBindKeys)) {
        const keys = [];
        for (const k of entry.requiredBindKeys) {
          if (typeof k !== 'string' || k.length === 0) continue;
          keys.push(k);
        }
        if (keys.length > 0) kindRequiredBindKeys.set(name, keys);
      }
    }
  }
  cachedRegistry = {
    names,
    identifierToKinds,
    externalNames,
    kindShapes,
    kindIdentifiers,
    kindLegacyBindKeys,
    kindRequiredBindKeys,
  };
  cachedFor = file;
  return cachedRegistry;
}

// Convert an identifier semanticType (PascalCase, e.g. `RoleId`) to the
// bind-key convention (`roleId`). Used by the bind-key validation
// check; matches the convention all entity kinds in the spec follow,
// with a small set of grandfathered exceptions handled by
// `legacyBindKeys` in semantic-kinds.json.
function camelCaseIdentifier(id) {
  if (typeof id !== 'string' || id.length === 0) return id;
  return id[0].toLowerCase() + id.slice(1);
}

module.exports = (input, _opts, _context) => {
  const errors = [];
  const {
    names: registry,
    identifierToKinds,
    externalNames,
    kindShapes,
    kindIdentifiers,
    kindLegacyBindKeys,
    kindRequiredBindKeys,
  } = loadRegistry();

  // The CI runs spectral twice: once on the bundled entry point
  // (rest-api.yaml) and once per-file across the glob. The cross-reference
  // walk at the end relies on every producer op being visible in `paths` —
  // true for the bundled pass, false for the per-file pass (e.g.
  // tenants.yaml has edges binding User, but createUser lives in
  // users.yaml). On fragment files (no `openapi:` at root) we still run
  // every per-operation check (unknown kind, illegal direct
  // establishes/requires of an external-entity, edge-endpoint
  // single-owner resolution) because those only need the registry plus
  // the operation in front of them. Only the producer-existence
  // cross-reference is gated.
  const crossReferenceEnabled = typeof input?.openapi === 'string';

  const paths = input?.paths;
  if (!paths || typeof paths !== 'object') return errors;

  const established = new Set();
  // { kind, method, pathKey, source: 'requires' | 'edge-endpoint',
  //   semanticType?: string }
  const required = [];

  for (const [pathKey, pathItem] of Object.entries(paths)) {
    if (!pathItem || typeof pathItem !== 'object') continue;
    for (const [method, op] of Object.entries(pathItem)) {
      if (!HTTP_METHODS.has(method) || !op || typeof op !== 'object') continue;

      // Lazily collected — only the first establishes/requires on the
      // operation pays the cost of walking parameters and body schemas.
      let availableMembers = null;
      const members = () => {
        if (availableMembers === null) {
          availableMembers = collectAvailableMembers(pathItem, op);
        }
        return availableMembers;
      };

      const est = op['x-semantic-establishes'];
      if (est && typeof est.kind === 'string') {
        // Track per-op establishes errors so we can gate the
        // `established.add(est.kind)` below: a producer with a broken
        // establishes block (unknown kind, illegal external-entity,
        // shape-vs-registry mismatch, or unresolved/multi-owner edge
        // endpoint) does not actually produce a usable kind for
        // downstream chain planning. Adding it eagerly would suppress
        // the orphan error on every consumer of that kind, hiding the
        // real downstream impact behind the local producer error.
        const errorsBeforeEstablishes = errors.length;
        if (!registry.has(est.kind)) {
          errors.push({
            message: `Unknown semantic kind '${est.kind}' on ${method.toUpperCase()} ${pathKey} (x-semantic-establishes). Add it to zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json or fix the typo.`,
            path: ['paths', pathKey, method, 'x-semantic-establishes', 'kind'],
          });
        } else if (externalNames.has(est.kind)) {
          errors.push({
            message: `Semantic kind '${est.kind}' is registered as 'shape: external-entity' and cannot be established. External entities are minted outside the Camunda REST API and may only be referenced via membership-edge identifiedBy tuples.`,
            path: ['paths', pathKey, method, 'x-semantic-establishes', 'kind'],
          });
        } else {
          // Shape consistency: the operation's `shape` (defaulting to
          // 'entity' when omitted, per semantic-establishes-shape) must
          // match the registry. Otherwise an op annotating an edge kind
          // as entity would silently skip edge-endpoint resolution and
          // the implicit requires it derives, masking real coverage gaps.
          const registeredShape = kindShapes.get(est.kind);
          const declaredShape =
            typeof est.shape === 'string' ? est.shape : 'entity';
          if (
            typeof registeredShape === 'string' &&
            registeredShape !== 'external-entity' &&
            registeredShape !== declaredShape
          ) {
            errors.push({
              message: `Semantic kind '${est.kind}' is registered as 'shape: ${registeredShape}' but x-semantic-establishes on ${method.toUpperCase()} ${pathKey} declares 'shape: ${declaredShape}'${typeof est.shape === 'string' ? '' : ' (default)'}. Either fix the operation to use 'shape: ${registeredShape}' or update the registry entry in semantic-kinds.json.`,
              path: [
                'paths',
                pathKey,
                method,
                'x-semantic-establishes',
                ...(typeof est.shape === 'string' ? ['shape'] : ['kind']),
              ],
            });
          }

          // Entity-tuple validation (camunda/camunda#52322 review):
          // for entity producers, every identifiedBy[].semanticType
          // must be a registered identifier of SOME entity kind.
          // Without this, a producer can claim to establish kind X via
          // a typo or stale identifier name and the orphan check on
          // every consumer of X is silently satisfied.
          //
          // Composite-key entities (e.g. TenantClusterVariable, whose
          // identity is (tenantId, name)) legitimately reference a
          // foreign-but-registered identifier in their identifiedBy:
          // the foreign identifier is part of the composite key but is
          // owned by another entity for the single-owner edge
          // resolution rule. The relaxed check (registered SOMEWHERE)
          // catches the bug class the reviewer identified (unregistered
          // typos) without breaking composite entities. A stricter
          // own-identifiers-only check would require either a
          // `compositeKey: true` registry marker or a per-kind override
          // and is intentionally deferred.
          //
          // Edge producers are excluded - their identifiedBy
          // semanticType cross-references foreign entity identifiers by
          // design (handled by the edge-endpoint resolution below).
          if (
            registeredShape === 'entity' &&
            declaredShape === 'entity' &&
            Array.isArray(est.identifiedBy)
          ) {
            for (let i = 0; i < est.identifiedBy.length; i++) {
              const item = est.identifiedBy[i];
              if (!item || typeof item !== 'object') continue;
              const st = item.semanticType;
              if (typeof st !== 'string' || st.length === 0) continue;
              if (!identifierToKinds.has(st)) {
                errors.push({
                  message: `x-semantic-establishes.identifiedBy[${i}].semanticType '${st}' on ${method.toUpperCase()} ${pathKey} is not declared as an identifier of any registered entity kind in semantic-kinds.json. Either fix the semanticType or add it to the appropriate kind's 'identifiers'.`,
                  path: [
                    'paths',
                    pathKey,
                    method,
                    'x-semantic-establishes',
                    'identifiedBy',
                    i,
                    'semanticType',
                  ],
                });
              }
            }
          }

          // Hole E: producer must include at least one of the
          // establishing kind's OWN identifiers in `identifiedBy`
          // (camunda/camunda#52322 review). Without this, an operation
          // can claim to establish kind K using only foreign
          // identifiers, satisfying the producer-existence gate so
          // downstream orphan errors disappear, even though the
          // producer never produces an identifier of K and consumers
          // have no way to obtain K's actual identifier tuple.
          //
          // Skipped when the kind has no registered `identifiers`
          // (e.g. registry-only kinds with composite-only identity);
          // the entity-tuple check above already validates each
          // semanticType is registered somewhere.
          //
          // Skipped for edge producers — by design, every edge
          // identifiedBy entry is a foreign identifier resolved via
          // single-owner edge-endpoint resolution.
          if (
            registeredShape === 'entity' &&
            declaredShape === 'entity' &&
            Array.isArray(est.identifiedBy)
          ) {
            const ownIds = kindIdentifiers.get(est.kind);
            if (ownIds && ownIds.size > 0) {
              const hasOwn = est.identifiedBy.some(
                (it) => it && typeof it === 'object' && ownIds.has(it.semanticType),
              );
              if (!hasOwn) {
                const ownList = Array.from(ownIds).join(', ');
                errors.push({
                  message: `x-semantic-establishes on ${method.toUpperCase()} ${pathKey} claims to establish kind '${est.kind}' but none of its identifiedBy entries reference one of the kind's own identifiers [${ownList}] (registered in semantic-kinds.json). A producer that only references foreign identifiers does not actually produce '${est.kind}'; downstream consumers would be unable to obtain the kind's identifier tuple. Add an identifiedBy entry whose semanticType is one of [${ownList}], or fix the establishing 'kind'.`,
                  path: [
                    'paths',
                    pathKey,
                    method,
                    'x-semantic-establishes',
                    'identifiedBy',
                  ],
                });
              }
            }
          }

          // Hole D: foreign-identifier-without-requires
          // (camunda/camunda#52322 review).
          //
          // Composite-key entity producers may legitimately reference a
          // foreign-but-registered identifier in `identifiedBy` (e.g.
          // `createTenantClusterVariable` carries `TenantId` in its
          // identity tuple even though `TenantId` is owned by `Tenant`).
          // The runtime depends on the foreign entity already existing
          // — `createTenantClusterVariable` returns 404 when the
          // referenced tenant is unknown. The annotation must mirror
          // that runtime dependency by declaring `x-semantic-requires`
          // on the foreign owning kind. Otherwise downstream chain
          // planners walk the producer as a root, synthesise an
          // arbitrary identifier value, and the engine rejects it.
          //
          // Skipped when the foreign identifier is also one of the
          // establishing kind's own registered identifiers — that is
          // the documented multi-owner sibling pattern (e.g.
          // `ClusterVariableName` is owned by both `GlobalClusterVariable`
          // and `TenantClusterVariable` because they are parallel
          // variants, not parent/child entities).
          //
          // Skipped for edge producers — the edge-endpoint resolution
          // below derives implicit `requires` from `identifiedBy` for
          // them by design (registry header `$comment` documents this).
          //
          // External-entity foreign owners (e.g. ClientId owned by
          // Client) cannot be referenced via `x-semantic-requires`
          // because direct establishes/requires of an external-entity
          // is forbidden. The only valid pattern is the per-tuple
          // `acceptsExternal: true` opt-out, mirroring how edge
          // endpoints declare bimodal acceptance. Anything else is an
          // unreachable orphan and is flagged below.
          if (
            registeredShape === 'entity' &&
            declaredShape === 'entity' &&
            Array.isArray(est.identifiedBy)
          ) {
            const ownIds = kindIdentifiers.get(est.kind) || new Set();
            const reqRaw = op['x-semantic-requires'];
            const reqList = Array.isArray(reqRaw)
              ? reqRaw
              : reqRaw && typeof reqRaw === 'object'
              ? [reqRaw]
              : [];
            const requiredKindsOnOp = new Set(
              reqList
                .map((r) => r && r.kind)
                .filter((k) => typeof k === 'string'),
            );
            for (let i = 0; i < est.identifiedBy.length; i++) {
              const item = est.identifiedBy[i];
              if (!item || typeof item !== 'object') continue;
              const st = item.semanticType;
              if (typeof st !== 'string' || st.length === 0) continue;
              // Multi-owner sibling pattern: identifier is also one of
              // the establishing kind's own identifiers. Accept.
              if (ownIds.has(st)) continue;
              const owners = identifierToKinds.get(st);
              if (!owners) continue; // unregistered - flagged above
              const foreignOwners = owners.filter((o) => o !== est.kind);
              if (foreignOwners.length === 0) continue;
              const foreignEntityOwners = foreignOwners.filter(
                (o) => kindShapes.get(o) === 'entity',
              );
              const foreignExternalOwners = foreignOwners.filter(
                (o) => kindShapes.get(o) === 'external-entity',
              );
              // External-entity foreign owners
              // (camunda/camunda#52322 review): an external-entity
              // kind can never appear as an `x-semantic-requires`
              // target (the no-direct-establishes/requires-on-
              // external-entity rule forbids it). If an entity
              // producer's identity tuple borrows an identifier whose
              // only foreign owner is external-entity (e.g. ClientId
              // owned by Client), the producer can never be reached
              // via the chain planner: nothing can supply the
              // identifier value because there is no producer for the
              // external entity in this API. The only valid pattern
              // is the per-tuple opt-out `acceptsExternal: true`,
              // mirroring how edge endpoints declare bimodal
              // acceptance. Without that flag, the producer is an
              // unreachable orphan; flag it so orphan detection is
              // not silently suppressed.
              if (
                foreignEntityOwners.length === 0 &&
                foreignExternalOwners.length > 0 &&
                item.acceptsExternal !== true
              ) {
                const extList = foreignExternalOwners.join(', ');
                errors.push({
                  message: `x-semantic-establishes.identifiedBy[${i}] on ${method.toUpperCase()} ${pathKey} declares semanticType '${st}', which is owned only by external-entity kind${foreignExternalOwners.length > 1 ? 's' : ''} [${extList}]. External entities cannot be referenced via x-semantic-requires (forbidden by the no-direct-establishes/requires-on-external-entity rule), so this producer is unreachable via chain planning. Either set 'acceptsExternal: true' on this identifiedBy entry to declare that the site accepts an externally-minted ID, or remove the foreign identifier from identifiedBy.`,
                  path: [
                    'paths',
                    pathKey,
                    method,
                    'x-semantic-establishes',
                    'identifiedBy',
                    i,
                    'semanticType',
                  ],
                });
                continue;
              }
              if (foreignEntityOwners.length === 0) continue;
              const satisfied = foreignEntityOwners.some((o) =>
                requiredKindsOnOp.has(o),
              );
              if (satisfied) continue;
              const ownersList = foreignEntityOwners.join(', ');
              errors.push({
                message: `x-semantic-establishes.identifiedBy[${i}] on ${method.toUpperCase()} ${pathKey} declares semanticType '${st}', which is owned by foreign entity kind${foreignEntityOwners.length > 1 ? 's' : ''} [${ownersList}], but the operation does not declare a corresponding x-semantic-requires. The runtime depends on the referenced entity already existing; add x-semantic-requires for one of [${ownersList}] (or remove the foreign identifier from identifiedBy if there is genuinely no such dependency).`,
                path: [
                  'paths',
                  pathKey,
                  method,
                  'x-semantic-establishes',
                  'identifiedBy',
                  i,
                  'semanticType',
                ],
              });
            }
          }
        }

        // Edge endpoint resolution. For shape: edge, every identifiedBy entry's
        // semanticType must resolve to exactly one registered entity. The
        // resolved entity becomes an implicit requires.
        if (est.shape === 'edge' && Array.isArray(est.identifiedBy)) {
          for (let i = 0; i < est.identifiedBy.length; i++) {
            const item = est.identifiedBy[i];
            if (!item || typeof item !== 'object') continue;
            const st = item.semanticType;
            if (typeof st !== 'string' || st.length === 0) continue;
            const owners = identifierToKinds.get(st);
            if (!owners || owners.length === 0) {
              errors.push({
                message: `Edge endpoint semanticType '${st}' on ${method.toUpperCase()} ${pathKey} (x-semantic-establishes.identifiedBy[${i}]) is not declared as an identifier of any registered entity kind. Add it to the entity's 'identifiers' in semantic-kinds.json or fix the typo.`,
                path: [
                  'paths',
                  pathKey,
                  method,
                  'x-semantic-establishes',
                  'identifiedBy',
                  i,
                  'semanticType',
                ],
              });
              continue;
            }
            if (owners.length > 1) {
              errors.push({
                message: `Edge endpoint semanticType '${st}' on ${method.toUpperCase()} ${pathKey} resolves to multiple entity kinds in semantic-kinds.json: ${owners.join(', ')}. Each semantic identifier must be owned by exactly one entity.`,
                path: [
                  'paths',
                  pathKey,
                  method,
                  'x-semantic-establishes',
                  'identifiedBy',
                  i,
                  'semanticType',
                ],
              });
            }
            for (const ownerKind of owners) {
              // Per-tuple bimodal opt-out (camunda/camunda#52322): when the
              // identifiedBy entry sets `acceptsExternal: true`, this
              // specific edge endpoint accepts either an in-API producer or
              // an externally-minted ID. Skip pushing the implicit
              // requires so the producer-existence cross-reference does
              // not flag missing-producer for this tuple. The single-owner
              // resolution above still runs, so typos and registry-config
              // errors are still caught.
              if (item.acceptsExternal === true) continue;
              required.push({
                kind: ownerKind,
                method,
                pathKey,
                source: 'edge-endpoint',
                semanticType: st,
              });
            }
          }
        }

        // Existence: every identifiedBy entry must reference a member
        // that actually exists on the operation (camunda/camunda#52413).
        if (Array.isArray(est.identifiedBy)) {
          for (let i = 0; i < est.identifiedBy.length; i++) {
            const item = est.identifiedBy[i];
            if (!item || typeof item !== 'object') continue;
            if (typeof item.in !== 'string' || typeof item.name !== 'string') continue;
            const m = members();
            if (item.in === 'body') {
              if (!m.hasRequestBody) {
                errors.push({
                  message: `x-semantic-establishes.identifiedBy[${i}] on ${method.toUpperCase()} ${pathKey} references body member '${item.name}', but the operation declares no requestBody. Either add a requestBody with that property or move the identifier off the body.`,
                  path: ['paths', pathKey, method, 'x-semantic-establishes', 'identifiedBy', i, 'name'],
                });
              } else if (m.bodyState === 'non-object') {
                errors.push({
                  message: `x-semantic-establishes.identifiedBy[${i}] on ${method.toUpperCase()} ${pathKey} references body member '${item.name}', but the requestBody schema resolves to a non-object type (scalar or array) and cannot have top-level properties. Move the identifier off the body or change the requestBody schema to an object.`,
                  path: ['paths', pathKey, method, 'x-semantic-establishes', 'identifiedBy', i, 'name'],
                });
              } else if (m.bodyState === 'walked' && !m.bodyProps.has(item.name)) {
                errors.push({
                  message: `x-semantic-establishes.identifiedBy[${i}] on ${method.toUpperCase()} ${pathKey} references body member '${item.name}', but no requestBody media-type schema declares a top-level property of that name. Either add the property to the request body or fix the binding.`,
                  path: ['paths', pathKey, method, 'x-semantic-establishes', 'identifiedBy', i, 'name'],
                });
              }
            } else if (PARAM_LOCATIONS.has(item.in) && !m[item.in].has(item.name)) {
              errors.push({
                message: `x-semantic-establishes.identifiedBy[${i}] on ${method.toUpperCase()} ${pathKey} references ${item.in} parameter '${item.name}', but no such ${item.in} parameter is declared on the operation (or its path item). Add the parameter or fix the binding.`,
                path: ['paths', pathKey, method, 'x-semantic-establishes', 'identifiedBy', i, 'name'],
              });
            }
          }
        }

        // Gate the producer-existence side of the cross-reference walk:
        // only count this op as a producer of `est.kind` if its
        // establishes block is well-formed:
        //   1. No errors raised by this function above (unknown kind,
        //      illegal external-entity, shape-vs-registry mismatch, or
        //      unresolved/multi-owner edge endpoint).
        //   2. Structural shape matches the `semantic-establishes-shape`
        //      Spectral rule. Re-checked here because errors raised by
        //      *other* rules are not visible in this function's local
        //      `errors[]`, so a producer that fails the shape rule
        //      (e.g. `identifiedBy: []`, missing `identifiedBy`, typo'd
        //      top-level key) would otherwise still be admitted here
        //      and mask the orphan error on every downstream consumer.
        // Otherwise consumers of this kind must still see the orphan
        // error so the downstream impact is visible.
        if (
          errors.length === errorsBeforeEstablishes &&
          isWellFormedEstablishes(est)
        ) {
          established.add(est.kind);
        }
      }

      const req = op['x-semantic-requires'];
      if (req && typeof req.kind === 'string') {
        required.push({
          kind: req.kind,
          method,
          pathKey,
          source: 'requires',
        });
        if (!registry.has(req.kind)) {
          errors.push({
            message: `Unknown semantic kind '${req.kind}' on ${method.toUpperCase()} ${pathKey} (x-semantic-requires). Add it to zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json or fix the typo.`,
            path: ['paths', pathKey, method, 'x-semantic-requires', 'kind'],
          });
        } else if (externalNames.has(req.kind)) {
          errors.push({
            message: `Semantic kind '${req.kind}' is registered as 'shape: external-entity' and cannot be required directly. External entities have no producer in this API; reference them via a membership edge instead.`,
            path: ['paths', pathKey, method, 'x-semantic-requires', 'kind'],
          });
        }

        // Existence: every bind entry must reference a member that
        // actually exists on the operation (camunda/camunda#52413).
        if (req.bind && typeof req.bind === 'object') {
          for (const [bindKey, binding] of Object.entries(req.bind)) {
            if (!binding || typeof binding !== 'object') continue;
            if (typeof binding.from !== 'string' || typeof binding.name !== 'string') continue;
            const m = members();
            if (binding.from === 'body') {
              if (!m.hasRequestBody) {
                errors.push({
                  message: `x-semantic-requires.bind.${bindKey} on ${method.toUpperCase()} ${pathKey} references body member '${binding.name}', but the operation declares no requestBody. Either add a requestBody with that property or move the binding off the body.`,
                  path: ['paths', pathKey, method, 'x-semantic-requires', 'bind', bindKey, 'name'],
                });
              } else if (m.bodyState === 'non-object') {
                errors.push({
                  message: `x-semantic-requires.bind.${bindKey} on ${method.toUpperCase()} ${pathKey} references body member '${binding.name}', but the requestBody schema resolves to a non-object type (scalar or array) and cannot have top-level properties. Move the binding off the body or change the requestBody schema to an object.`,
                  path: ['paths', pathKey, method, 'x-semantic-requires', 'bind', bindKey, 'name'],
                });
              } else if (m.bodyState === 'walked' && !m.bodyProps.has(binding.name)) {
                errors.push({
                  message: `x-semantic-requires.bind.${bindKey} on ${method.toUpperCase()} ${pathKey} references body member '${binding.name}', but no requestBody media-type schema declares a top-level property of that name. Either add the property to the request body or fix the binding.`,
                  path: ['paths', pathKey, method, 'x-semantic-requires', 'bind', bindKey, 'name'],
                });
              }
            } else if (PARAM_LOCATIONS.has(binding.from) && !m[binding.from].has(binding.name)) {
              errors.push({
                message: `x-semantic-requires.bind.${bindKey} on ${method.toUpperCase()} ${pathKey} references ${binding.from} parameter '${binding.name}', but no such ${binding.from} parameter is declared on the operation (or its path item). Add the parameter or fix the binding.`,
                path: ['paths', pathKey, method, 'x-semantic-requires', 'bind', bindKey, 'name'],
              });
            }
          }

          // Bind-key tuple validation (camunda/camunda#52322 review):
          // for entity-kind consumers, every bind key must equal
          // `camelCase(identifier)` for one of the kind's registered
          // identifiers in semantic-kinds.json (or appear in the kind's
          // explicit `legacyBindKeys` escape hatch). Catches typos like
          // `rolId` for `kind: Role`, where the existing existence
          // check only validates `binding.name` against the operation's
          // members and the typo'd bind key (the map key, not
          // `binding.name`) lints clean. Skipped for edge kinds because
          // their bind keys span multiple endpoint entities and the
          // registry does not enumerate edge bind keys directly; the
          // edge-endpoint resolution above guards the producer side.
          // Skipped for unknown / external kinds (already errored above).
          if (registry.has(req.kind) && !externalNames.has(req.kind)) {
            const registeredShape = kindShapes.get(req.kind);
            if (registeredShape === 'entity') {
              const ids = kindIdentifiers.get(req.kind);
              if (ids && ids.size > 0) {
                const legacy = kindLegacyBindKeys.get(req.kind);
                const allowedKeys = new Set();
                for (const id of ids) allowedKeys.add(camelCaseIdentifier(id));
                if (legacy) for (const k of legacy) allowedKeys.add(k);
                for (const bindKey of Object.keys(req.bind)) {
                  if (!allowedKeys.has(bindKey)) {
                    const allowedList = Array.from(allowedKeys).sort().join(', ');
                    errors.push({
                      message: `x-semantic-requires.bind has key '${bindKey}' on ${method.toUpperCase()} ${pathKey}, but kind '${req.kind}' declares identifiers [${Array.from(ids).join(', ')}] in semantic-kinds.json (expected bind key one of: [${allowedList}]). Fix the bind key, add the missing identifier to the kind's 'identifiers', or \u2014 only if the wire field is a published name that cannot be renamed \u2014 add '${bindKey}' to the kind's 'legacyBindKeys'.`,
                      path: ['paths', pathKey, method, 'x-semantic-requires', 'bind', bindKey],
                    });
                  }
                }
              }
            }
          }

          // Hole F: consumer bind must cover the full identifier tuple
          // (camunda/camunda#52322 review). The bind-key validation
          // above only checks that each key is *allowed*, not that all
          // *required* keys are present. For composite-key entities
          // (e.g. TenantClusterVariable whose identity is (tenantId,
          // name)), a consumer binding only `name` lints clean but
          // leaves the planner with an under-specified reference the
          // runtime cannot resolve.
          //
          // Required tuple per kind:
          //   - If the registry declares an explicit `requiredBindKeys`
          //     array (composite-key entities like
          //     TenantClusterVariable), every listed key MUST appear
          //     in `bind`. The verifier accepts the keys verbatim;
          //     legacy aliases are not substituted because the registry
          //     has already chosen the canonical wire form.
          //   - Otherwise, for each registered identifier `id`, the
          //     bind must contain `camelCase(id)` OR one of the kind's
          //     `legacyBindKeys`. Single-identifier kinds with a
          //     legacy alias (e.g. GlobalListener -> `id`) are covered
          //     by either form.
          //
          // Skipped for unknown / external kinds (already errored
          // above) and for kinds with no registered identifiers (the
          // bind-key allow-list above is the only check that applies).
          if (registry.has(req.kind) && !externalNames.has(req.kind)) {
            const registeredShape = kindShapes.get(req.kind);
            if (registeredShape === 'entity') {
              const bindKeys = new Set(Object.keys(req.bind));
              const required = kindRequiredBindKeys.get(req.kind);
              if (required) {
                const missing = required.filter((k) => !bindKeys.has(k));
                if (missing.length > 0) {
                  errors.push({
                    message: `x-semantic-requires.bind on ${method.toUpperCase()} ${pathKey} for kind '${req.kind}' is missing required bind key${missing.length > 1 ? 's' : ''} [${missing.join(', ')}] (the kind declares requiredBindKeys [${required.join(', ')}] in semantic-kinds.json because its identity is composite). Add the missing bind${missing.length > 1 ? 'ings' : 'ing'} so the planner has the full identifier tuple.`,
                    path: ['paths', pathKey, method, 'x-semantic-requires', 'bind'],
                  });
                }
              } else {
                const ids = kindIdentifiers.get(req.kind);
                if (ids && ids.size > 0) {
                  const legacy = kindLegacyBindKeys.get(req.kind) || new Set();
                  const missing = [];
                  for (const id of ids) {
                    const cc = camelCaseIdentifier(id);
                    const satisfied =
                      bindKeys.has(cc) || [...legacy].some((l) => bindKeys.has(l));
                    if (!satisfied) missing.push(cc);
                  }
                  if (missing.length > 0) {
                    const legacyHint = legacy.size > 0
                      ? ` (or one of legacyBindKeys [${[...legacy].join(', ')}])`
                      : '';
                    errors.push({
                      message: `x-semantic-requires.bind on ${method.toUpperCase()} ${pathKey} for kind '${req.kind}' does not bind identifier${missing.length > 1 ? 's' : ''} [${missing.join(', ')}]${legacyHint}. Every registered identifier of an entity kind must be bound so the planner can construct the full identifier tuple at runtime.`,
                      path: ['paths', pathKey, method, 'x-semantic-requires', 'bind'],
                    });
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Cross-reference: every required kind must be established somewhere.
  // Skip kinds that already failed the registry check — the message above
  // is the actionable one. Skip external entities — by definition they
  // have no producer in this API, and the direct-reject above already
  // catches anyone trying to require one with x-semantic-requires.
  // Skip entirely on fragment files: producers may live in sibling files
  // not visible in this pass. The bundled pass enforces it.
  if (!crossReferenceEnabled) return errors;
  for (const r of required) {
    if (!registry.has(r.kind)) continue;
    if (externalNames.has(r.kind)) continue;
    if (established.has(r.kind)) continue;
    if (r.source === 'edge-endpoint') {
      errors.push({
        message: `Semantic kind '${r.kind}' is required (derived from x-semantic-establishes.identifiedBy.semanticType '${r.semanticType}' on ${r.method.toUpperCase()} ${r.pathKey}) but no operation establishes it. Add x-semantic-establishes on the producer operation, or remove '${r.semanticType}' from the entity's 'identifiers' if it is not actually an endpoint of this edge.`,
        path: [
          'paths',
          r.pathKey,
          r.method,
          'x-semantic-establishes',
          'identifiedBy',
        ],
      });
    } else {
      errors.push({
        message: `Semantic kind '${r.kind}' is required by ${r.method.toUpperCase()} ${r.pathKey} (x-semantic-requires) but no operation establishes it. Add x-semantic-establishes on the producer operation.`,
        path: ['paths', r.pathKey, r.method, 'x-semantic-requires', 'kind'],
      });
    }
  }

  return errors;
};
