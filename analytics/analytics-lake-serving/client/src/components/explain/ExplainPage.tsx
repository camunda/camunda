/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
import { useLocation } from "react-router";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Separator,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@camunda/design-system";
import {
  api,
  type CohortCompareRequest,
  type ConditionsRequest,
  type DecomposeRequest,
  type ExemplarsRequest,
  type Finding,
  type InvestigateResponse,
  type ScreenRequest,
  type SeriesRequest,
  type ToolName,
} from "../../lib/api";
import type { ExplainPrefill } from "../../lib/explainNav";
import { EmptyTile } from "../common/EmptyTile";
import { EntryForm, type EntryFormValue } from "./EntryForm";
import { FindingCard } from "./FindingCard";
import { ChangepointPanel } from "./tools/ChangepointPanel";
import { CohortComparePanel } from "./tools/CohortComparePanel";
import { ConditionsPanel } from "./tools/ConditionsPanel";
import { DecomposePanel } from "./tools/DecomposePanel";
import { ExemplarsPanel } from "./tools/ExemplarsPanel";
import { ScreenPanel } from "./tools/ScreenPanel";
import { SeriesPanel } from "./tools/SeriesPanel";

const TOOL_LABELS: Record<ToolName, string> = {
  series: "Series",
  changepoint: "Changepoint",
  decompose: "Decompose",
  screen: "Screen",
  "cohort-compare": "Cohort compare",
  exemplars: "Exemplars",
  conditions: "Conditions",
};

/**
 * The Explain page: an entry form that runs POST /api/investigate and renders its ranked findings,
 * plus a manual-mode "Tools" section with one panel per investigate tool. A dashboard tile's ⌕ or
 * a finding's "edit & rerun" both land here -- the former pre-fills the entry form (via router
 * state, see lib/explainNav.ts), the latter switches straight to the finding's own tool tab,
 * pre-filled from its `toolParams`.
 */
export function ExplainPage() {
  const location = useLocation();
  const initialPrefill = (location.state as { prefill?: ExplainPrefill } | null)?.prefill;

  const [investigateResult, setInvestigateResult] = useState<InvestigateResponse | null>(null);
  const [investigateError, setInvestigateError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [activeTool, setActiveTool] = useState<ToolName>("series");
  const [toolParams, setToolParams] = useState<Record<string, unknown> | undefined>(undefined);

  const handleSubmit = (value: EntryFormValue) => {
    setSubmitting(true);
    setInvestigateError(null);
    api.investigate(value).then((res) => {
      if (!res.ok) {
        setInvestigateError(res.message);
        setInvestigateResult(null);
      } else {
        setInvestigateResult(res.data);
      }
      setSubmitting(false);
    });
  };

  const handleEditRerun = (finding: Finding) => {
    setActiveTool(finding.tool);
    setToolParams(finding.toolParams);
  };

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">Explain</h1>

      <Card>
        <CardHeader>
          <CardTitle>Investigate</CardTitle>
        </CardHeader>
        <CardContent>
          <EntryForm initial={initialPrefill} onSubmit={handleSubmit} submitting={submitting} />
        </CardContent>
      </Card>

      {investigateError ? (
        <EmptyTile heading="Investigate failed" description={investigateError} />
      ) : null}

      {investigateResult ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-medium text-neutral-foreground-muted">
            Findings ({investigateResult.findings.length})
          </h2>
          {investigateResult.findings.length === 0 ? (
            <EmptyTile heading="No findings" description="Nothing stood out for this entity/window." />
          ) : (
            investigateResult.findings
              .slice()
              .sort((a, b) => b.rung - a.rung)
              .map((f) => <FindingCard key={f.id} finding={f} onEditRerun={handleEditRerun} />)
          )}
        </div>
      ) : null}

      <Separator />

      <div className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-neutral-foreground-muted">Tools (manual mode)</h2>
        <Tabs value={activeTool} onValueChange={(v) => setActiveTool(v as ToolName)}>
          <TabsList>
            {(Object.keys(TOOL_LABELS) as ToolName[]).map((tool) => (
              <TabsTrigger key={tool} value={tool}>
                {TOOL_LABELS[tool]}
              </TabsTrigger>
            ))}
          </TabsList>
          <TabsContent value="series" className="pt-4">
            <SeriesPanel
              prefill={activeTool === "series" ? (toolParams as Partial<SeriesRequest> | undefined) : undefined}
            />
          </TabsContent>
          <TabsContent value="changepoint" className="pt-4">
            <ChangepointPanel
              prefill={
                activeTool === "changepoint" ? (toolParams as Partial<SeriesRequest> | undefined) : undefined
              }
            />
          </TabsContent>
          <TabsContent value="decompose" className="pt-4">
            <DecomposePanel
              prefill={
                activeTool === "decompose" ? (toolParams as Partial<DecomposeRequest> | undefined) : undefined
              }
            />
          </TabsContent>
          <TabsContent value="screen" className="pt-4">
            <ScreenPanel
              prefill={activeTool === "screen" ? (toolParams as Partial<ScreenRequest> | undefined) : undefined}
            />
          </TabsContent>
          <TabsContent value="cohort-compare" className="pt-4">
            <CohortComparePanel
              prefill={
                activeTool === "cohort-compare"
                  ? (toolParams as Partial<CohortCompareRequest> | undefined)
                  : undefined
              }
            />
          </TabsContent>
          <TabsContent value="exemplars" className="pt-4">
            <ExemplarsPanel
              prefill={
                activeTool === "exemplars" ? (toolParams as Partial<ExemplarsRequest> | undefined) : undefined
              }
            />
          </TabsContent>
          <TabsContent value="conditions" className="pt-4">
            <ConditionsPanel
              prefill={
                activeTool === "conditions" ? (toolParams as Partial<ConditionsRequest> | undefined) : undefined
              }
            />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
