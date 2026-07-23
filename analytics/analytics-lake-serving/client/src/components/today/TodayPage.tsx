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
import { api, type Filters, type Finding } from "../../lib/api";
import { useExplainNavigate } from "../../lib/explainNav";
import { describeFinding } from "../../lib/findingText";
import { formatCount, formatDuration, formatPercent1 } from "../../lib/format";
import { useAppData } from "../../lib/appData";
import type { DashboardRange } from "../../lib/range";
import { useGlobalRange } from "../../lib/rangeContext";
import { findDim, findMeasure } from "../../lib/registryHelpers";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";

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
  const top = card.findings ? [...card.findings].sort((a, b) => b.rung - a.rung)[0] : undefined;

  return (
    <div className={`border-l-2 py-1 pl-3 ${top ? "border-primary" : "border-border"}`}>
      <div className="text-xs font-medium text-neutral-foreground-muted">{card.processId}</div>
      {card.error ? (
        <p className="text-sm text-neutral-foreground-muted">Not available: {card.error}</p>
      ) : !card.findings || card.findings.length === 0 ? (
        <p className="text-sm text-neutral-foreground-muted">No notable shifts for this process.</p>
      ) : top ? (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm">{describeFinding(top)}</p>
          <button
            type="button"
            className="shrink-0 text-xs text-primary underline"
            onClick={() =>
              goToExplain({
                entity: "instances",
                measure: durationMeasure,
                filters: { [instancesProcessDim]: card.processId } as Filters,
                from: range.from,
                to: range.to,
              })
            }
          >
            open in Ask why
          </button>
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

function ObjectTypeCard({ type }: { type: string }) {
  const [count, setCount] = useState<number | null>(null);
  const [atLimit, setAtLimit] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api.objects.list({ type, status: "OPEN", limit: OBJECT_CARD_PAGE_SIZE, offset: 0 }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
      } else {
        setCount(result.data.rows.length);
        setAtLimit(result.data.rows.length >= OBJECT_CARD_PAGE_SIZE);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  return (
    <Link to={`/objects/${encodeURIComponent(type)}?status=OPEN`} className="block">
      <Card className="transition-colors hover:bg-neutral-background-subtle">
        <CardContent className="flex flex-col gap-1.5 p-4">
          <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
            {type}
          </span>
          {loading ? (
            <span className="text-sm text-neutral-foreground-muted">Loading…</span>
          ) : error ? (
            <span className="text-sm text-neutral-foreground-muted">Not available</span>
          ) : (
            <span className="text-2xl font-semibold tabular-nums">
              {atLimit ? `${formatCount(count)}+` : formatCount(count)} open
            </span>
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
