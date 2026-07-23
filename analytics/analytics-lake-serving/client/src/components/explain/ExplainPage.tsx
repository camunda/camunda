/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * "Ask why", question-first (design sketch Exhibit C): an "ask about" context (entity/measure/
 * window/filters, editable behind a "change…" chip) plus three question chips. Picking a question
 * runs the matching tool call(s) and renders the result as asserted-sentence finding cards, each
 * with its evidence one click away (see FindingCard/EvidencePanel). The seven tool panels that used
 * to be the whole page now live one level down, in a collapsed "Advanced" section -- still fully
 * functional, and reused unmodified as every finding's own evidence renderer.
 */
import { useEffect, useState } from "react";
import { useLocation } from "react-router";
import {
  Badge,
  Button,
  Card,
  CardContent,
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@camunda/design-system";
import {
  type CohortCompareRequest,
  type ConditionsRequest,
  type DecomposeRequest,
  type ExemplarsRequest,
  type Finding,
  api,
  type ScreenRequest,
  type SeriesRequest,
  type ToolName,
} from "../../lib/api";
import { TOOL_LABELS } from "../../lib/findingText";
import { formatDuration } from "../../lib/format";
import { defaultRange } from "../../lib/range";
import { useAppData } from "../../lib/appData";
import type { ExplainPrefill } from "../../lib/explainNav";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";
import { AskAboutEditor } from "./AskAboutEditor";
import { FindingCard } from "./FindingCard";
import { type AskAboutContext, runCostliestStep, runSlowVsFast } from "./questions";
import { ChangepointPanel } from "./tools/ChangepointPanel";
import { CohortComparePanel } from "./tools/CohortComparePanel";
import { ConditionsPanel } from "./tools/ConditionsPanel";
import { DecomposePanel } from "./tools/DecomposePanel";
import { ExemplarsPanel } from "./tools/ExemplarsPanel";
import { ScreenPanel } from "./tools/ScreenPanel";
import { SeriesPanel } from "./tools/SeriesPanel";

type QuestionKind = "why" | "slow" | "cost";

const QUESTIONS: { kind: QuestionKind; label: string }[] = [
  { kind: "why", label: "Why did it change?" },
  { kind: "slow", label: "What makes the slow ones slow?" },
  { kind: "cost", label: "Which step costs the most?" },
];

/** Builds the ask-about context from a dashboard/finding prefill if one arrived, else a blank
 * context on the app's default 24h window -- entity stays "" until either the prefill names one or
 * the registry loads and {@link ExplainPage} backfills a sensible default (see its effect below). */
function initialAskAbout(prefill: ExplainPrefill | undefined): AskAboutContext {
  if (prefill) {
    const r = defaultRange();
    return {
      entity: prefill.entity,
      measure: prefill.measure ?? null,
      quantile: prefill.quantile ?? null,
      filters: prefill.filters ?? {},
      from: prefill.from ?? r.from,
      to: prefill.to ?? r.to,
    };
  }
  const r = defaultRange();
  return { entity: "", measure: null, quantile: 0.95, filters: {}, from: r.from, to: r.to };
}

/** The compact "ask about" summary chip, e.g. "p95 instances where processId=order-intake · last
 * 24.0 h" -- composed from whatever the registry actually reports rather than a business-friendly
 * template, since neither the entity nor its filters carry a display name in the contract (the
 * language map's own scope is limited to tool/kind labels, not registry names). */
function askAboutSummaryText(ctx: AskAboutContext): string {
  const measureLabel = ctx.quantile != null ? `p${Math.round(ctx.quantile * 100)}` : (ctx.measure ?? "cnt");
  const filterEntries = Object.entries(ctx.filters);
  const filterSuffix = filterEntries.length > 0 ? ` where ${filterEntries.map(([k, v]) => `${k}=${v}`).join(", ")}` : "";
  const rangeLabel = `last ${formatDuration(Math.max(0, ctx.to - ctx.from))}`;
  return `${measureLabel} of ${ctx.entity}${filterSuffix} · ${rangeLabel}`;
}

export function ExplainPage() {
  const location = useLocation();
  const initialPrefill = (location.state as { prefill?: ExplainPrefill } | null)?.prefill;
  const { entities } = useAppData();

  const [askAbout, setAskAbout] = useState<AskAboutContext>(() => initialAskAbout(initialPrefill));
  const [editing, setEditing] = useState(false);

  // Backfill a default entity once the registry loads, if the reader arrived with no prefill and
  // hasn't picked one yet -- never overrides a prefill or a reader's own in-progress choice.
  useEffect(() => {
    if (initialPrefill || askAbout.entity !== "" || entities.length === 0) {
      return;
    }
    const preferred = entities.find((e) => e.name === "instances") ?? entities[0];
    setAskAbout((s) => (s.entity === "" ? { ...s, entity: preferred.name } : s));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entities]);

  const [activeQuestion, setActiveQuestion] = useState<QuestionKind | null>(null);
  const [findings, setFindings] = useState<Finding[] | null>(null);
  const [emptyReason, setEmptyReason] = useState<string | null>(null);
  const [questionError, setQuestionError] = useState<string | null>(null);
  const [loadingQuestion, setLoadingQuestion] = useState(false);

  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [activeTool, setActiveTool] = useState<ToolName>("series");
  const [toolParams, setToolParams] = useState<Record<string, unknown> | undefined>(undefined);

  const handleEditRerun = (finding: Finding) => {
    setActiveTool(finding.tool);
    setToolParams(finding.toolParams);
    setAdvancedOpen(true);
  };

  const runQuestion = async (kind: QuestionKind) => {
    if (!askAbout.entity) {
      return;
    }
    setActiveQuestion(kind);
    setLoadingQuestion(true);
    setQuestionError(null);
    setEmptyReason(null);
    setFindings(null);

    if (kind === "why") {
      const res = await api.investigate({
        entity: askAbout.entity,
        measure: askAbout.measure,
        quantile: askAbout.quantile,
        filters: askAbout.filters,
        from: askAbout.from,
        to: askAbout.to,
      });
      if (!res.ok) {
        setQuestionError(res.message);
      } else if (res.data.findings.length === 0) {
        setEmptyReason("Nothing stood out for this entity/window.");
        setFindings([]);
      } else {
        setFindings(res.data.findings);
      }
    } else {
      const res = kind === "slow" ? await runSlowVsFast(askAbout, entities) : await runCostliestStep(askAbout, entities);
      if (!res.ok) {
        setQuestionError(res.message);
      } else if (res.finding == null) {
        setEmptyReason(res.emptyReason ?? "Nothing found for this question.");
        setFindings([]);
      } else {
        setFindings([res.finding]);
      }
    }
    setLoadingQuestion(false);
  };

  const showEditor = editing || askAbout.entity === "";

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">Ask why</h1>

      <Card>
        <CardContent className="flex flex-col gap-4 p-4">
          <div className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
              Ask about
            </span>
            {showEditor ? (
              <AskAboutEditor
                value={askAbout}
                onApply={(v) => {
                  setAskAbout(v);
                  setEditing(false);
                }}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="accent">{askAboutSummaryText(askAbout)}</Badge>
                <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>
                  change…
                </Button>
              </div>
            )}
          </div>

          {!showEditor ? (
            <div className="flex flex-wrap gap-2">
              {QUESTIONS.map((q) => (
                <Button
                  key={q.kind}
                  size="sm"
                  variant={activeQuestion === q.kind ? "default" : "secondary"}
                  disabled={!askAbout.entity}
                  onClick={() => runQuestion(q.kind)}
                >
                  {q.label}
                </Button>
              ))}
            </div>
          ) : null}
        </CardContent>
      </Card>

      {loadingQuestion ? (
        <LoadingTile />
      ) : questionError ? (
        <EmptyTile heading="Not available yet" description={questionError} />
      ) : findings ? (
        findings.length === 0 ? (
          <EmptyTile heading="Nothing stood out" description={emptyReason ?? "No findings for this question."} />
        ) : (
          <div className="flex flex-col gap-3">
            {findings
              .slice()
              .sort((a, b) => b.rung - a.rung)
              .map((f) => (
                <FindingCard key={f.id} finding={f} onEditRerun={handleEditRerun} />
              ))}
          </div>
        )
      ) : null}

      <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
        <CollapsibleTrigger className="flex w-full items-center justify-between rounded border border-border px-3 py-2 text-left text-sm text-neutral-foreground-muted hover:bg-neutral-background-subtle">
          <span>Advanced: {Object.values(TOOL_LABELS).join(" · ")}</span>
          <span aria-hidden="true">{advancedOpen ? "▲" : "▼"}</span>
        </CollapsibleTrigger>
        <CollapsibleContent className="pt-4">
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
        </CollapsibleContent>
      </Collapsible>
    </div>
  );
}
