/**
 * Backwards-compat collapse for the version map.
 *
 * When a property re-appears in a later version on a different schema branch
 * (oneOf split, sibling reintroduction for backwards-compatibility),
 * branch-tagging gives every occurrence its own entry. Collapse to the MIN
 * version across the (endpoint, location, property) triple ONLY when a
 * fully-tagless ancestor exists — i.e. the property had a
 * pre-polymorphic-split flat form. Without a tagless ancestor, the
 * sibling-variant collapse (sibling-variant-collapse.mjs) still collapses
 * occurrences whose schema is identical on every branch; occurrences with
 * different schemas (e.g. a discriminator-wrapper child pinning the
 * discriminator to its own value) keep their own version.
 *
 * Deliberately not the sibling-variant collapse's normalizeQname /
 * isBranchTagged: those also strip inline branch tags (`oneOf[0].foo`), which
 * would group an inline-branch property with a plain `foo` on the same
 * endpoint. This pass has no schema check, so it must keep grouping only what
 * it grouped before the sibling-variant collapse existed.
 */

import { compareVersions } from './sibling-variant-collapse.mjs';

const hasLeadingBranch = (qname) => /^[A-Z][^.|]*\./.test(qname);

export function isTagless(qname) {
  return !qname.includes('|') && !hasLeadingBranch(qname);
}

export function normalizeQname(qname) {
  return qname.replace(/\|[^.]+/g, '').replace(/^[A-Z][^.|]*\./, '');
}

/**
 * Apply the backwards-compat collapse to `versionMap` in place.
 *
 * @param {object} versionMap  the builder's version map ({ properties, … })
 * @returns {Array<{qualifiedName: string, endpoint: string, location: string, from: string, to: string}>}
 *          the entries whose version was lowered
 */
export function collapseBackwardsCompat(versionMap) {
  const byTriple = new Map();
  for (const val of Object.values(versionMap.properties)) {
    const k = `${val.endpoint}|||${val.location}|||${normalizeQname(val.qualifiedName)}`;
    let bucket = byTriple.get(k);
    if (!bucket) {
      bucket = { hasTagless: false, min: val.version, entries: [] };
      byTriple.set(k, bucket);
    }
    bucket.entries.push(val);
    if (isTagless(val.qualifiedName)) bucket.hasTagless = true;
    if (compareVersions(val.version, bucket.min) < 0) bucket.min = val.version;
  }
  const collapsed = [];
  for (const bucket of byTriple.values()) {
    if (!bucket.hasTagless) continue;
    for (const val of bucket.entries) {
      if (compareVersions(bucket.min, val.version) < 0) {
        collapsed.push({
          qualifiedName: val.qualifiedName,
          endpoint: val.endpoint,
          location: val.location,
          from: val.version,
          to: bucket.min,
        });
        val.version = bucket.min;
      }
    }
  }
  return collapsed;
}
