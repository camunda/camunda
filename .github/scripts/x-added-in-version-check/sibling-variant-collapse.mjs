/**
 * Sibling-variant collapse for the version map.
 *
 * `addedInVersion` records the first version in which a client of an endpoint
 * could send or receive a property. Where the property is defined in the YAML
 * is an implementation detail, and this check must never require restructuring
 * the spec to pass.
 *
 * The builder tracks a property inside a `oneOf`/`anyOf` branch separately for
 * each branch (`…ById.businessId`, `…ByKey.businessId`). Its "backwards-compat
 * collapse" gives such a property the earliest version across branches only if
 * the property once existed outside any branch (a tagless ancestor). A property
 * that was added *after* the split and is then repeated on a new sibling branch
 * would otherwise look brand new on that branch, e.g. `businessId` (8.9) on
 * ProcessInstanceCreationInstructionById/ByKey, repeated on the new
 * ProcessInstanceCreationInstructionByVersionTag (8.10). Clients of
 * `POST /process-instances` have been able to send `businessId` since 8.9, so
 * the truthful label is 8.9. Without this collapse, the only way to make the
 * check accept it was to move the shared properties into an `allOf` base, which
 * changes the generated SDK types (camunda/camunda#67987).
 *
 * Rule: for every (endpoint, location, property path ignoring branch tags) with
 * occurrences on two or more branches, set every occurrence to the earliest
 * version among them, provided the property's schema is identical on every
 * branch once documentation is ignored. If the schemas differ, the occurrences
 * are different properties that happen to share a name, and each keeps its own
 * version.
 */

const DOC_KEYS = new Set(['description', 'example', 'examples', 'title', 'deprecated', 'externalDocs']);

export function compareVersions(a, b) {
  const [aMaj, aMin] = a.split('.').map(Number);
  const [bMaj, bMin] = b.split('.').map(Number);
  return aMaj - bMaj || aMin - bMin;
}

// A leading branch tag is either a schema name (`Foo.bar` — capitalised, per
// the repo's PascalCase schema convention) or an indexed inline-branch tag
// (`oneOf[0].bar`, `anyOf[2].baz`). A lowercase leading segment is an ordinary
// property (`filter.state`), never a branch tag.
const LEADING_BRANCH_TAG = /^(?:[A-Z][^.|]*|(?:oneOf|anyOf)\[\d+\])\./;
// A nested root union tags its inner branches with the outer branch's tag
// (`OldBranch|Inner.businessId`, `oneOf[0]|Inner.businessId`): the segment
// before the first `.` then carries a `|` tag yet is itself a branch tag, not
// a real property like `page|OffsetPagination`. Match the same tag shapes
// (PascalCase schema name or indexed inline tag) with a `|`-tagged suffix.
const LEADING_NESTED_ROOT_TAG = /^(?:[A-Z][^.|]*|(?:oneOf|anyOf)\[\d+\])\|[^.]+\./;

/**
 * Remove branch tags (`|Branch` segments and a leading `Branch.`) from a
 * qualified name. A root inline branch is tagged with its keyword and index
 * (`oneOf[0].foo`, `anyOf[2].bar`) rather than a schema name; a root branch
 * nested under another root branch carries both tags (`OldBranch|Inner.foo`,
 * `oneOf[0]|Inner.foo`) and loses them together.
 */
export function normalizeQname(qname) {
  // Decide on the original qname: a leading segment carrying a `|` tag
  // (`page|OffsetPagination`) is a real property whose tag is stripped below,
  // not a branch tag itself — unless the segment is itself a branch tag
  // (`OldBranch|Inner.`), the nested-root-union case.
  const withoutLeading = LEADING_BRANCH_TAG.test(qname)
    ? qname.replace(LEADING_BRANCH_TAG, '')
    : qname.replace(LEADING_NESTED_ROOT_TAG, '');
  return withoutLeading.replace(/\|[^.]+/g, '');
}

export function isBranchTagged(qname) {
  return (
    qname.includes('|') ||
    LEADING_BRANCH_TAG.test(qname) ||
    LEADING_NESTED_ROOT_TAG.test(qname)
  );
}

const SCHEMA_CONTAINER_KEYS = new Set([
  'items',
  'additionalProperties',
  'allOf',
  'anyOf',
  'oneOf',
  'not',
  'if',
  'then',
  'else',
  'contains',
  'propertyNames',
  'patternProperties',
  'dependentSchemas',
  '$defs',
  'definitions',
]);

/**
 * A schema with documentation and vendor extensions removed and keys sorted,
 * as a string. Keywords are stripped only where a schema object is expected:
 * `properties` maps property *names* to schemas, so a property named
 * `description` must survive; `enum`/`const`/`default` hold literal data that
 * may itself be a doc-keyed object (e.g. `default: {description: 'x'}`).
 */
export function schemaShape(node) {
  // mode: 'schema' — strip doc keys; 'map' — a name→schema dictionary whose
  // keys are data; 'literal' — arbitrary data, compared verbatim.
  const strip = (n, mode) => {
    if (Array.isArray(n)) {
      return n.map((e) => strip(e, mode === 'literal' ? 'literal' : 'schema'));
    }
    if (!n || typeof n !== 'object') return n;
    const out = {};
    for (const k of Object.keys(n).sort()) {
      if (mode === 'schema' && (DOC_KEYS.has(k) || k.startsWith('x-'))) continue;
      const childMode =
        mode === 'literal'
          ? 'literal'
          : mode === 'map'
            ? 'schema'
            : k === 'properties'
              ? 'map'
              : SCHEMA_CONTAINER_KEYS.has(k)
                ? 'schema'
                : 'literal';
      out[k] = strip(n[k], childMode);
    }
    return out;
  };
  return node === undefined ? undefined : JSON.stringify(strip(node, 'schema'));
}

/**
 * Decode one RFC 6901 JSON Pointer segment (`~1` -> `/`, `~0` -> `~`).
 * Bundler-generated refs into `#/paths/...` escape the `/` in path templates
 * (`#/paths/~1canonical/post`) and media types (`application~1json`), so the
 * literal key never matches the spec node.
 */
function unescapePointerSegment(seg) {
  return seg.replace(/~1/g, '/').replace(/~0/g, '~');
}

function nodeAt(root, path) {
  let node = root;
  // A leading `*.yaml` segment is the source-file attribution the builder
  // prefixes to cross-file paths for the verifier; the bundled spec has no
  // such top-level key, so drop it before traversing.
  const segments =
    typeof path?.[0] === 'string' && path[0].endsWith('.yaml') ? path.slice(1) : path;
  for (const seg of segments || []) {
    // Follow intermediate $refs: the bundler emits refs into `#/paths/…`,
    // whose traversal passes through nodes that are themselves `{ $ref }`.
    while (node?.$ref && typeof node.$ref === 'string' && node.$ref.startsWith('#/')) {
      node = nodeAt(root, node.$ref.slice(2).split('/').map(unescapePointerSegment));
    }
    node = node?.[unescapePointerSegment(seg)];
  }
  return node;
}

/**
 * Apply the sibling-variant collapse to `versionMap` in place.
 *
 * @param {object} versionMap  the builder's version map ({ properties, deletedProperties, … })
 * @param {object} spec        the latest bundled spec, which every entry's `path` points into
 * @returns {{ collapsed: Array<{propKey: string, from: string, to: string}>, skipped: string[] }}
 *          `skipped` lists groups left alone because the schemas differ and the versions differ.
 */
export function collapseSiblingVariants(versionMap, spec) {
  const deleted = versionMap.deletedProperties || {};
  const groups = new Map();
  for (const [propKey, entry] of Object.entries(versionMap.properties || {})) {
    if (deleted[propKey]) continue;
    if (!entry.qualifiedName || !isBranchTagged(entry.qualifiedName)) continue;
    const key = `${entry.endpoint} > ${entry.location} > ${normalizeQname(entry.qualifiedName)}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push([propKey, entry]);
  }

  const collapsed = [];
  const skipped = [];
  for (const [key, entries] of groups) {
    if (entries.length < 2) continue;
    const versions = new Set(entries.map(([, e]) => e.version));
    if (versions.size < 2) continue;
    const shapes = new Set(entries.map(([, e]) => schemaShape(nodeAt(spec, e.path))));
    if (shapes.size !== 1 || shapes.has(undefined)) {
      skipped.push(key);
      continue;
    }
    const min = [...versions].sort(compareVersions)[0];
    for (const [propKey, entry] of entries) {
      if (compareVersions(min, entry.version) < 0) {
        collapsed.push({ propKey, from: entry.version, to: min });
        entry.version = min;
      }
    }
  }
  return { collapsed, skipped };
}
