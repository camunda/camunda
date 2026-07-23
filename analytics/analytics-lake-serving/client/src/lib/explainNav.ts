/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useNavigate } from "react-router";
import type { Filters } from "./api";

/** The subset of an Explain entry-form's fields a dashboard tile's ⌕ button can usefully know up
 * front (its own entity/measure/window). Passed as router state, not a URL query string, since it
 * never needs to be a shareable/bookmarkable link -- only reachable by clicking through. */
export interface ExplainPrefill {
  entity: string;
  measure?: string | null;
  quantile?: number | null;
  filters?: Filters;
  from?: number;
  to?: number;
}

/** Navigates to /explain pre-filled with the given entity/measure/window, as the ⌕ buttons on
 * dashboard tiles and the "[edit & rerun]" action on a finding card both need. */
export function useExplainNavigate() {
  const navigate = useNavigate();
  return (prefill: ExplainPrefill) => navigate("/explain", { state: { prefill } });
}
