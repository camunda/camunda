/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type { EntityDescriptor } from "./api";

/**
 * The dashboard tiles are wired against entity/measure/dim names guessed from the spec's prose
 * (e.g. "instance_cohorts: completed-within-1h/1d") -- the contract itself only pins down the
 * seven tools' request/response shapes, not the registry's actual naming. These two helpers do a
 * case-insensitive substring match against whatever the registry (GET /api/registry) actually
 * reports, so a tile still finds its measure/dim if the real name is a reasonable variant of the
 * guess (e.g. "completedWithin1h" for a "1h" candidate); if nothing matches, callers render the
 * tile's informative empty state instead of guessing wrong and mislabeling a chart.
 */

/** Every name the entity accepts as a series/decompose `measure`: real measures, named counters,
 * and the bare row count's "cnt" spelling when the entity has one. */
export function measureNames(entity: EntityDescriptor | undefined): string[] {
  if (!entity) {
    return [];
  }
  return [...entity.measures.map((m) => m.name), ...entity.counters, ...(entity.hasCnt ? ["cnt"] : [])];
}

export function findMeasure(entity: EntityDescriptor | undefined, candidates: string[]): string | undefined {
  const names = measureNames(entity);
  for (const candidate of candidates) {
    const match = names.find((name) => name.toLowerCase().includes(candidate.toLowerCase()));
    if (match) {
      return match;
    }
  }
  return undefined;
}

/** Builds the dim filter an object-perspective tile needs: resolves the entity's object-type dim
 * (e.g. `object_type`) against the registry and keys the filter by its real name -- an empty
 * filter (rather than a guessed key the backend would 400 on) when the entity or dim is absent. */
export function objectTypeFilter(
  entities: EntityDescriptor[],
  entityName: string,
  objectType: string,
): Record<string, string> {
  const dim = findDim(
    entities.find((e) => e.name === entityName),
    ["object_type", "objecttype", "type"],
  );
  return dim ? { [dim]: objectType } : {};
}

export function findDim(entity: EntityDescriptor | undefined, candidates: string[]): string | undefined {
  if (!entity) {
    return undefined;
  }
  for (const candidate of candidates) {
    const match = entity.dims.find((d) => d.name.toLowerCase().includes(candidate.toLowerCase()));
    if (match) {
      return match.name;
    }
  }
  return undefined;
}

export function useFindEntity(entities: EntityDescriptor[], name: string): EntityDescriptor | undefined {
  return entities.find((e) => e.name === name);
}
