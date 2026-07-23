/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useAppData } from "../../../lib/appData";
import { findDim } from "../../../lib/registryHelpers";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile } from "../../common/EmptyTile";
import { DecomposeTile } from "./DecomposeTile";

/**
 * Thin wrapper around {@link DecomposeTile} that resolves its `dim` from a set of name candidates
 * against the live registry first (see lib/registryHelpers.ts), instead of assuming the spec's
 * prose name (e.g. "element_id") is the registry's exact spelling. Renders the tile's empty state
 * up front when the entity or dim isn't there, rather than firing a request bound to fail.
 */
export function DecomposeByDimTile({
  title,
  description,
  entity,
  measure = null,
  quantile = null,
  dimCandidates,
  from,
  to,
  topN,
  valueFormatter,
}: {
  title: string;
  description?: string;
  entity: string;
  measure?: string | null;
  quantile?: number | null;
  dimCandidates: string[];
  from: number;
  to: number;
  topN?: number;
  valueFormatter?: (v: number) => string;
}) {
  const { entities } = useAppData();
  const dim = findDim(
    entities.find((e) => e.name === entity),
    dimCandidates,
  );

  if (dim == null) {
    return (
      <ChartCard title={title} description={description}>
        <EmptyTile
          heading="Not available yet"
          description={`${entity} has no ${dimCandidates[0]}-like dimension in the registry yet.`}
        />
      </ChartCard>
    );
  }

  return (
    <DecomposeTile
      title={title}
      description={description}
      entity={entity}
      measure={measure}
      quantile={quantile}
      dim={dim}
      from={from}
      to={to}
      topN={topN}
      valueFormatter={valueFormatter}
    />
  );
}
