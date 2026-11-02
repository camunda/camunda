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

/** Remove a branch tag (`|Branch` segments and a leading `Branch.`) from a qualified name. */
export function normalizeQname(qname) {
  return qname.replace(/\|[^.]+/g, '').replace(/^[A-Z][^.|]*\./, '');
}

export function isBranchTagged(qname) {
  return qname.includes('|') || /^[A-Z][^.|]*\./.test(qname);
}

/** A schema with documentation and vendor extensions removed and keys sorted, as a string. */
export function schemaShape(node) {
  const strip = (n) => {
    if (Array.isArray(n)) return n.map(strip);
    if (!n || typeof n !== 'object') return n;
    const out = {};
    for (const k of Object.keys(n).sort()) {
      if (DOC_KEYS.has(k) || k.startsWith('x-')) continue;
      out[k] = strip(n[k]);
    }
    return out;
  };
  return node === undefined ? undefined : JSON.stringify(strip(node));
}

function nodeAt(root, path) {
  let node = root;
  for (const seg of path || []) node = node?.[seg];
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
