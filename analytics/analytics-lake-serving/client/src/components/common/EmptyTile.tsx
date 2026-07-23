/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { EmptyState } from "@camunda/design-system";

/**
 * The informative "not available yet" body every tile/page renders instead of crashing when its
 * entity/dataset is absent from the registry, its API call 404/400/500s, or it resolved with zero
 * rows. Fills the parent's fixed-height plot area (see ChartCard) so the tile keeps its normal
 * footprint in the grid whether or not it has data.
 */
export function EmptyTile({
  heading,
  description,
}: {
  heading: string;
  description?: string;
}) {
  return (
    <div className="flex h-full w-full items-center justify-center">
      <EmptyState heading={heading} description={description} size="sm" headingLevel={4} />
    </div>
  );
}

/** Renders a plain loading placeholder (deliberately not an EmptyState -- "loading" isn't an empty
 * or error condition, just pending) so it's visually distinct from "not available". */
export function LoadingTile() {
  return (
    <div className="flex h-full w-full items-center justify-center text-sm text-neutral-foreground-muted">
      Loading…
    </div>
  );
}
