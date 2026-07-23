/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * The new landing page (design sketch Exhibit A): a headline strip, a "needs attention" digest
 * built from the investigate planner's own findings (promoted from Ask why's side feature to the
 * front page, per the sketch), and one open-count card per discovered object type. Nothing here
 * needs new collection -- every call below is an existing tools/series, tools/decompose, investigate,
 * or objects/list request.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle } from "@camunda/design-system";
import { api, type Filters, type Finding, type ObjectRow } from "../../lib/api";
import { useExplainNavigate, type ExplainPrefill } from "../../lib/explainNav";
import { composeDigestSentence, confidenceLabel } from "../../lib/findingText";
import { formatCount, formatDuration, formatPercent1 } from "../../lib/format";
import { useAppData } from "../../lib/appData";
import type { DashboardRange } from "../../lib/range";
import { useGlobalRange } from "../../lib/rangeContext";
import { findDim, findMeasure } from "../../lib/registryHelpers";
import { BoldNumbers } from "../common/BoldNumbers";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";
import { FindingCard } from "../explain/FindingCard";
import { useClosingBehavior } from "../common/useClosingBehavior";

/** How the digest picks which processes to look at, and how many investigate calls it's allowed
 * to make -- capped per the spec ("cap total requests (3 investigates max), run them in
 * parallel"). */
const DIGEST_PROCESS_CAP = 3;
const PROCESS_ID_CANDIDATES = ["bpmnProcessId", "processId", "process"];
const DURATION_MEASURE_CANDIDATES = ["duration_ms", "durationms", "duration"];

// ---------------------------------------------------------------------------------------------
// Headline strip
// ---------------------------------------------------------------------------------------------

interface WindowDelta {
  current: number | null;
  previous: number | null;
}

/** One entity/measure's value over exactly the selected window, as a single number -- requests the
 * series tool with grainMinutes spanning the whole window (so it comes back as one bucket, or a
 * couple at most near a boundary) rather than adding a dedicated "total" endpoint. A quantile is
 * averaged across whatever buckets come back (it's not additive); a plain measure/counter is
 * summed. */
async function fetchWindowValue(
  entity: string,
  measure: string | null,
  quantile: number | null,
  from: number,
  to: number,
): Promise<number | null> {
  const span = Math.max(1, to - from);
  const grainMinutes = Math.max(1, Math.ceil(span / 60_000));
  const res = await api.tools.series({ entity, measure, quantile, filters: {}, from, to, grainMinutes });
  if (!res.ok || res.data.points.length === 0) {
    return null;
  }
  const values = res.data.points.map((p) => p.value);
  return quantile != null
    ? values.reduce((a, b) => a + b, 0) / values.length
    : values.reduce((a, b) => a + b, 0);
}

/** The stat's value for the selected window, plus the same measure over the immediately-preceding
 * equal-length window -- every headline "vs previous period" comparison derives from the same
 * global range, per the design sketch. */
async function fetchWithDelta(
  entity: string,
  measure: string | null,
  quantile: number | null,
  from: number,
  to: number,
): Promise<WindowDelta> {
  const span = to - from;
  const [current, previous] = await Promise.all([
    fetchWindowValue(entity, measure, quantile, from, to),
    fetchWindowValue(entity, measure, quantile, from - span, from),
  ]);
  return { current, previous };
}

function DeltaChip({ current, previous }: WindowDelta) {
  if (current == null || previous == null || previous === 0) {
    return <Badge variant="neutral">—</Badge>;
  }
  const change = (current - previous) / previous;
  if (Math.abs(change) < 0.005) {
    return <Badge variant="neutral">—</Badge>;
  }
  const up = change > 0;
  return (
    <Badge variant={up ? "warning" : "success"} title="vs the previous equal-length period">
      {up ? "▲" : "▼"} {formatPercent1(Math.abs(change))}
    </Badge>
  );
}

function StatBox({
  label,
  value,
  delta,
  note,
}: {
  label: string;
  value: string;
  delta?: WindowDelta;
  note?: string;
}) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-1.5 p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
          {label}
        </span>
        <span className="text-3xl font-semibold tabular-nums">{value}</span>
        {delta ? (
          <DeltaChip {...delta} />
        ) : note ? (
          <span className="text-xs text-neutral-foreground-muted">{note}</span>
        ) : null}
      </CardContent>
    </Card>
  );
}

function HeadlineStrip({ range }: { range: DashboardRange }) {
  const { from, to } = range;
  const [completed, setCompleted] = useState<WindowDelta>({ current: null, previous: null });
  const [p95, setP95] = useState<WindowDelta>({ current: null, previous: null });
  const [started, setStarted] = useState<WindowDelta>({ current: null, previous: null });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      fetchWithDelta("instances", "cnt", null, from, to),
      fetchWithDelta("instances", null, 0.95, from, to),
      fetchWithDelta("instance_starts", "cnt", null, from, to),
    ]).then(([completedResult, p95Result, startedResult]) => {
      if (cancelled) {
        return;
      }
      setCompleted(completedResult);
      setP95(p95Result);
      setStarted(startedResult);
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [from, to]);

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatBox
        label={`Instances completed · ${range.label}`}
        value={loading ? "…" : formatCount(completed.current)}
        delta={completed}
      />
      <StatBox
        label="p95 cycle time"
        value={loading ? "…" : p95.current != null ? formatDuration(p95.current) : "–"}
        delta={p95}
      />
      {/* "Open instances" is omitted here: there's no cheap open-process-instance count in this
       * lane's contract -- api.objects.list counts business objects (orders, ...), not raw process
       * instances, and guessing at an "open" filter on the instances entity itself risks a wrong
       * key the backend 400s on. Flagged in the lane report rather than forced in. */}
      <StatBox label="Open instances" value="–" note="Not available in this lane -- see report" />
      <StatBox
        label={`Instances started · ${range.label}`}
        value={loading ? "…" : formatCount(started.current)}
        delta={started}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Needs-attention digest
// ---------------------------------------------------------------------------------------------

interface ProcessDigestCard {
  processId: string;
  findings: Finding[] | null;
  error?: string;
}

/** Left-border severity stripe, banded off the leading finding's rung -- the design sketch's
 * `.finding` (strong/accent) vs `.finding.quiet` (structural/muted) distinction, extended to three
 * bands since a digest card's rung already has three meaningful levels (see RungBadge/
 * confidenceLabel's own banding, kept in lockstep with this). */
function severityStripeClass(rung: number): string {
  if (rung >= 3) {
    return "border-red-400 dark:border-red-500";
  }
  if (rung === 2) {
    return "border-primary";
  }
  return "border-blue-300 dark:border-blue-800";
}

function DigestCardRow({
  card,
  range,
  instancesProcessDim,
  durationMeasure,
}: {
  card: ProcessDigestCard;
  range: DashboardRange;
  instancesProcessDim: string;
  durationMeasure: string;
}) {
  const goToExplain = useExplainNavigate();
  const [showEvidence, setShowEvidence] = useState(false);

  const cardPrefill: ExplainPrefill = {
    entity: "instances",
    measure: durationMeasure,
    filters: { [instancesProcessDim]: card.processId } as Filters,
    from: range.from,
    to: range.to,
  };
  // Every finding in this card came from the same investigate call, so "edit & rerun" on any of
  // them is exactly "open in Ask why" with this card's own context -- no per-finding toolParams
  // parsing needed.
  const editRerun = () => goToExplain(cardPrefill);

  if (card.error) {
    return (
      <div className="border-l-2 border-border py-1.5 pl-3">
        <div className="text-xs font-medium text-neutral-foreground-muted">{card.processId}</div>
        <p className="text-sm text-neutral-foreground-muted">Not available: {card.error}</p>
      </div>
    );
  }
  if (!card.findings || card.findings.length === 0) {
    return (
      <div className="border-l-2 border-border py-1.5 pl-3">
        <div className="text-xs font-medium text-neutral-foreground-muted">{card.processId}</div>
        <p className="text-sm text-neutral-foreground-muted">No notable shifts for this process.</p>
      </div>
    );
  }

  const composition = composeDigestSentence(card.findings);

  // Rule 5 (see composeDigestSentence): nothing stitched -- render every finding as its own card
  // rather than forcing a narrative out of weak material.
  if (!composition) {
    return (
      <div className="border-l-2 border-border py-1.5 pl-3">
        <div className="text-xs font-medium text-neutral-foreground-muted">{card.processId}</div>
        <div className="mt-2 flex flex-col gap-2">
          {card.findings.map((f) => (
            <FindingCard key={f.id} finding={f} onEditRerun={editRerun} />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className={`border-l-2 py-1.5 pl-3 ${severityStripeClass(composition.confidenceFrom.rung)}`}>
      <div className="text-xs font-medium text-neutral-foreground-muted">{card.processId}</div>
      <p className="text-sm">
        <BoldNumbers text={composition.sentence} />
      </p>
      <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-neutral-foreground-muted">
        <span>{confidenceLabel(composition.confidenceFrom.rung)}</span>
        <button type="button" className="underline" onClick={() => setShowEvidence((s) => !s)}>
          {showEvidence ? "hide evidence" : "see evidence"}
        </button>
        <button type="button" className="text-primary underline" onClick={editRerun}>
          open in Ask why
        </button>
      </div>
      {showEvidence ? (
        <div className="mt-2 flex flex-col gap-2">
          {composition.parts.map((f) => (
            <FindingCard key={f.id} finding={f} onEditRerun={editRerun} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

/**
 * "Needs attention": for the top {@link DIGEST_PROCESS_CAP} processes by started count this
 * window (via tools/decompose on instance_starts), runs one investigate call per process and shows
 * its top finding as a plain sentence -- the investigate planner's own findings, promoted from Ask
 * why's side feature to the front page, per the design sketch. Every request degrades independently
 * (one process's investigate failing doesn't blank the other cards).
 */
function NeedsAttentionDigest({ range }: { range: DashboardRange }) {
  const { entities } = useAppData();
  const [cards, setCards] = useState<ProcessDigestCard[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const startsProcessDim = findDim(
    entities.find((e) => e.name === "instance_starts"),
    PROCESS_ID_CANDIDATES,
  );
  const instancesProcessDim = findDim(
    entities.find((e) => e.name === "instances"),
    PROCESS_ID_CANDIDATES,
  );
  const durationMeasure = findMeasure(
    entities.find((e) => e.name === "instances"),
    DURATION_MEASURE_CANDIDATES,
  );
  const ready = startsProcessDim != null && instancesProcessDim != null && durationMeasure != null;

  useEffect(() => {
    if (!ready || startsProcessDim == null || instancesProcessDim == null || durationMeasure == null) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    const { from, to } = range;
    const span = to - from;

    api.tools
      .decompose({
        entity: "instance_starts",
        measure: "cnt",
        quantile: null,
        window: { from, to },
        baseline: { from: from - span, to: from },
        dim: startsProcessDim,
      })
      .then((decomposeResult) => {
        if (cancelled) {
          return;
        }
        if (!decomposeResult.ok) {
          setError(decomposeResult.message);
          setLoading(false);
          return;
        }
        const topProcesses = [...decomposeResult.data.rows]
          .sort((a, b) => b.current - a.current)
          .slice(0, DIGEST_PROCESS_CAP)
          .map((r) => r.value);

        if (topProcesses.length === 0) {
          setCards([]);
          setLoading(false);
          return;
        }

        Promise.all(
          topProcesses.map((processId) =>
            api.investigate({
              entity: "instances",
              measure: durationMeasure,
              quantile: null,
              filters: { [instancesProcessDim]: processId } as Filters,
              from,
              to,
            }).then((res) => ({ processId, res })),
          ),
        ).then((results) => {
          if (cancelled) {
            return;
          }
          setCards(
            results.map(({ processId, res }) => ({
              processId,
              findings: res.ok ? res.data.findings : null,
              error: res.ok ? undefined : res.message,
            })),
          );
          setLoading(false);
        });
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range, ready]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Needs attention</CardTitle>
        <CardDescription>Found automatically, ranked by process throughput</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {!ready ? (
          <EmptyTile
            heading="Not available yet"
            description="The registry doesn't expose a process dimension or duration measure for instances yet."
          />
        ) : loading ? (
          <LoadingTile />
        ) : error ? (
          <EmptyTile heading="Not available yet" description={error} />
        ) : !cards || cards.length === 0 ? (
          <p className="text-sm text-neutral-foreground-muted">
            Nothing stood out for the busiest processes in this window.
          </p>
        ) : (
          cards.map((card) => (
            <DigestCardRow
              key={card.processId}
              card={card}
              range={range}
              instancesProcessDim={instancesProcessDim!}
              durationMeasure={durationMeasure!}
            />
          ))
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------------------------
// Object-type cards
// ---------------------------------------------------------------------------------------------

const OBJECT_CARD_PAGE_SIZE = 1000;

/**
 * One object-type card in the sketch's compact form: label, big count line, then a quieter
 * "oldest ... · view" line (Exhibit A: `<span class="v">593 open</span><br>oldest 46 min ·
 * <u>view</u>`). "Open" only means something for a type with an actual closing rule -- see
 * {@link useClosingBehavior}'s doc comment for the heuristic that swaps in "known" and drops the
 * status filter for a type that's never observed closing (e.g. customers).
 */
function ObjectTypeCard({ type }: { type: string }) {
  const closing = useClosingBehavior(type);
  const [rows, setRows] = useState<ObjectRow[] | null>(null);
  const [atLimit, setAtLimit] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Wait for the closing-behavior heuristic before picking a status -- avoids firing the list
    // call twice (once guessing OPEN, then again once "never-closes" is known).
    if (closing === "loading") {
      return;
    }
    let cancelled = false;
    setError(null);
    const status = closing === "never-closes" ? "ALL" : "OPEN";
    api.objects.list({ type, status, limit: OBJECT_CARD_PAGE_SIZE, offset: 0 }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
      } else {
        setRows(result.data.rows);
        setAtLimit(result.data.rows.length >= OBJECT_CARD_PAGE_SIZE);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [type, closing]);

  const loading = closing === "loading" || (rows == null && error == null);
  const label = closing === "never-closes" ? "known" : "open";
  const oldestAge =
    rows && rows.length > 0
      ? formatDuration(Date.now() - Math.min(...rows.map((r) => new Date(r.firstSeen).getTime())))
      : null;
  const statusParam = closing === "never-closes" ? "" : "?status=OPEN";

  return (
    <Link to={`/objects/${encodeURIComponent(type)}${statusParam}`} className="block">
      <Card className="transition-colors hover:bg-neutral-background-subtle">
        <CardContent className="flex flex-col gap-1 p-4">
          <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
            {type}
          </span>
          {loading ? (
            <span className="text-sm text-neutral-foreground-muted">Loading…</span>
          ) : error ? (
            <span className="text-sm text-neutral-foreground-muted">Not available</span>
          ) : (
            <>
              <span className="text-2xl font-semibold tabular-nums">
                {atLimit ? `${formatCount(rows?.length)}+` : formatCount(rows?.length)} {label}
              </span>
              <span className="text-xs text-neutral-foreground-muted">
                {oldestAge != null ? `oldest ${oldestAge} · ` : ""}
                <span className="text-primary underline">view</span>
              </span>
            </>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}

/** One card per discovered object type -- hidden entirely (like the perspective switcher) when
 * GET /api/objects/types returned none. */
function ObjectTypeCards() {
  const { objectTypes, objectTypesLoaded } = useAppData();
  if (!objectTypesLoaded || objectTypes.length === 0) {
    return null;
  }
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {objectTypes.map((t) => (
        <ObjectTypeCard key={t} type={t} />
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------

/** Today: the new landing page (design sketch Exhibit A) -- the pulse of the last {@link
 * DashboardRange.label} plus what needs attention, before the reader has to know our schema. */
export function TodayPage() {
  const { range } = useGlobalRange();
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">Today</h1>
      <HeadlineStrip range={range} />
      <NeedsAttentionDigest range={range} />
      <ObjectTypeCards />
    </div>
  );
}
