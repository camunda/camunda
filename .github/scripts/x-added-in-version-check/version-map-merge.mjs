/**
 * Merge one version's extracted operations and properties into the version
 * map: record what is first seen in this version, refresh path attribution
 * for what already exists, and record what the previous version had but this
 * one no longer does. Moved unchanged from build-bundler-version-map.mjs's
 * main() so it can be tested on fixtures.
 *
 * @param {object} versionMap  { operations, properties, deletedOperations, deletedProperties }, updated in place
 * @param {string} version     the version being merged, e.g. '8.9'
 * @param {{operations: Map, properties: Map}} current   extractSpecData's result for `version`
 * @param {{operations: Map|null, properties: Map|null}} previous  the previous version's result, or nulls for the first version
 * @returns {{newOps: number, newProps: number, deletedOps: number, deletedProps: number}}
 */
export function mergeVersion(versionMap, version, { operations, properties }, previous) {
  // Record operations first seen in this version (and refresh path attribution)
  let newOps = 0;
  for (const [opKey, opData] of operations) {
    if (!versionMap.operations[opKey]) {
      versionMap.operations[opKey] = {
        version,
        summary: opData.summary,
        path: opData.path,
      };
      newOps++;
    } else {
      // Keep the original first-seen version, but refresh the path to the
      // most recent attribution (later versions split into per-resource files).
      versionMap.operations[opKey].path = opData.path;
      // Backfill summary if it was missing in the version where it was first seen.
      if (!versionMap.operations[opKey].summary && opData.summary) {
        versionMap.operations[opKey].summary = opData.summary;
      }
    }
  }

  // Record properties first seen in this version (and refresh path attribution)
  let newProps = 0;
  for (const [propKey, propData] of properties) {
    if (!versionMap.properties[propKey]) {
      versionMap.properties[propKey] = {
        version,
        ...propData,
      };
      newProps++;
    } else {
      // Preserve the first-seen version; update path/depth to current version's.
      const existing = versionMap.properties[propKey];
      versionMap.properties[propKey] = {
        ...existing,
        location: propData.location,
        endpoint: propData.endpoint,
        property: propData.property,
        qualifiedName: propData.qualifiedName,
        depth: propData.depth,
        path: propData.path,
      };
    }
  }

  // Detect deleted operations
  let deletedOps = 0;
  if (previous.operations) {
    for (const [opKey, opData] of previous.operations) {
      if (!operations.has(opKey) && !versionMap.deletedOperations[opKey]) {
        versionMap.deletedOperations[opKey] = {
          removedIn: version,
          summary: opData.summary,
        };
        // Strip the stale `path` from the surviving entry in `operations`.
        // Its value was last refreshed in the version where the op still
        // existed, which differs between pipelines depending on whether
        // that version's upstream/ dir was populated. The op stays listed
        // so downstream consumers still see when it was introduced.
        if (versionMap.operations[opKey]) {
          delete versionMap.operations[opKey].path;
        }
        deletedOps++;
      }
    }
  }

  // Detect deleted properties
  let deletedProps = 0;
  if (previous.properties) {
    // A property may legitimately migrate from an inline position to inside a
    // deeper *container* across versions (e.g. `processDefinitionVersion` moved
    // from the request body root in 8.7 into `ProcessInstanceCreationInstructionById`
    // in 8.8). Container migrations keep the qname tagless, so treat the field
    // as still present only when the surviving match also lives in a tagless
    // qname.
    //
    // Relocations into a `oneOf`/`anyOf` branch (qname carries a `|BranchTag`
    // segment) are NOT treated as still-present: the parent schema genuinely
    // stopped declaring the property, and downstream consumers need to see the
    // entry in `deletedProperties` so they can suppress annotations on the
    // pre-split shape. The branch-tagged entry remains in `properties` with
    // its own `version` reflecting when the branch first declared it.
    const currentTaglessLeafs = new Set();
    for (const propData of properties.values()) {
      if (propData.qualifiedName && propData.qualifiedName.includes('|')) continue;
      currentTaglessLeafs.add(`${propData.endpoint}|||${propData.location}|||${propData.property}`);
    }
    for (const [propKey, propData] of previous.properties) {
      if (properties.has(propKey)) continue;
      if (versionMap.deletedProperties[propKey]) continue;
      const leafKey = `${propData.endpoint}|||${propData.location}|||${propData.property}`;
      if (currentTaglessLeafs.has(leafKey)) continue;
      versionMap.deletedProperties[propKey] = {
        removedIn: version,
        endpoint: propData.endpoint,
      };
      // Strip the stale `path` from the surviving entry in `properties`,
      // mirroring the deletedOperations treatment.
      if (versionMap.properties[propKey]) {
        delete versionMap.properties[propKey].path;
      }
      deletedProps++;
    }
  }

  return { newOps, newProps, deletedOps, deletedProps };
}
