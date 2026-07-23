/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type {
  CohortCompareRequest,
  ConditionsRequest,
  DecomposeRequest,
  ExemplarsRequest,
  Finding,
  ScreenRequest,
  SeriesRequest,
} from "../../lib/api";
import { ChangepointPanel } from "./tools/ChangepointPanel";
import { CohortComparePanel } from "./tools/CohortComparePanel";
import { ConditionsPanel } from "./tools/ConditionsPanel";
import { DecomposePanel } from "./tools/DecomposePanel";
import { ExemplarsPanel } from "./tools/ExemplarsPanel";
import { ScreenPanel } from "./tools/ScreenPanel";
import { SeriesPanel } from "./tools/SeriesPanel";

/**
 * A finding's evidence, rendered by reusing the same seven tool panels the Advanced section shows
 * -- per the design sketch's "Advanced: ... " note ("the seven tool panels remain -- one level
 * down ... they are the evidence renderers too"). Dispatches on `finding.tool` (the same
 * discriminant {@link ExplainPage}'s "Edit & rerun" already switches on) and hands the panel
 * `finding.toolParams` as its prefill with `autoRun` set, so expanding "see evidence" needs exactly
 * the one click the sketch shows -- no second "Run" click to see the chart/table.
 */
export function EvidencePanel({ finding }: { finding: Finding }) {
  const params = finding.toolParams;
  switch (finding.tool) {
    case "series":
      return <SeriesPanel prefill={params as Partial<SeriesRequest>} autoRun />;
    case "changepoint":
      return <ChangepointPanel prefill={params as Partial<SeriesRequest>} autoRun />;
    case "decompose":
      return <DecomposePanel prefill={params as Partial<DecomposeRequest>} autoRun />;
    case "screen":
      return <ScreenPanel prefill={params as Partial<ScreenRequest>} autoRun />;
    case "cohort-compare":
      return <CohortComparePanel prefill={params as Partial<CohortCompareRequest>} autoRun />;
    case "exemplars":
      return <ExemplarsPanel prefill={params as Partial<ExemplarsRequest>} autoRun />;
    case "conditions":
      return <ConditionsPanel prefill={params as Partial<ConditionsRequest>} autoRun />;
    default:
      // Exhaustive per ToolName -- only reachable if the backend ever adds an eighth tool this
      // client doesn't know about yet.
      return <p className="text-sm text-neutral-foreground-muted">No evidence renderer for this finding yet.</p>;
  }
}
