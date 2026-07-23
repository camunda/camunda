/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * The Processes picker (design sketch's "Two global controls" -> Definition selection, level 1):
 * one card per deployed process from `GET /api/definitions/list`, each enriched with a throughput
 * sparkline + p95 over the shared global range. A warehouse can also have process activity that
 * predates `process_definitions` (an older lake) -- those process ids show up in the
 * instance_starts decompose below but never in the definitions list, so this page unions both
 * sources rather than only ever showing definitions: a process with metrics but no definition still
 * gets a card (its version chip/diagram degrade gracefully on its detail page, same as any other
 * missing-definition case in this app).
 */
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { Badge, Card, CardContent } from "@camunda/design-system";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, XAxis, YAxis } from "recharts";
import { api } from "../../lib/api";
import { useAppData } from "../../lib/appData";
import { chartColor } from "../../lib/chartColors";
import { formatDuration } from "../../lib/format";
import { processesApi, type ProcessDefinitionSummary } from "../../lib/processesApi";
import type { DashboardRange } from "../../lib/range";
import { useGlobalRange } from "../../lib/rangeContext";
import { findDim, findMeasure } from "../../lib/registryHelpers";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";

const PROCESS_ID_CANDIDATES = ["bpmnProcessId", "processId", "process"];
const DURATION_MEASURE_CANDIDATES = ["duration_ms", "durationms", "duration"];

interface ProcessListItem {
  processId: string;
  latestVersion: number | null;
  hasDefinition: boolean;
}

function mergeProcessList(
  definitions: ProcessDefinitionSummary[],
  metricsProcessIdsByVolume: string[],
): ProcessListItem[] {
  const items: ProcessListItem[] = definitions.map((d) => ({
    processId: d.processId,
    latestVersion: d.latestVersion,
    hasDefinition: true,
  }));
  const known = new Set(items.map((i) => i.processId));
  for (const processId of metricsProcessIdsByVolume) {
    if (!known.has(processId)) {
      items.push({ processId, latestVersion: null, hasDefinition: false });
      known.add(processId);
    }
  }
  return items;
}

// ---------------------------------------------------------------------------------------------
// Per-card enrichment: a throughput sparkline (instance_starts, cnt) + p95 cycle time (instances,
// quantile 0.95), same tools/series shape TodayPage's headline strip uses -- degrades to a plain
// card (no chart, "–" for p95) if the registry doesn't have the expected dim/measure yet, rather
// than skipping the card entirely.
// ---------------------------------------------------------------------------------------------

interface SparkPoint {
  t: string;
  v: number;
}

function Sparkline({ points }: { points: SparkPoint[] }) {
  if (points.length === 0) {
    return <span className="text-xs text-neutral-foreground-muted">No throughput data</span>;
  }
  return (
    <div className="h-10 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
          <CartesianGrid stroke="transparent" />
          <XAxis dataKey="t" hide />
          <YAxis hide domain={[0, "auto"]} />
          <Line type="monotone" dataKey="v" stroke={chartColor(3)} strokeWidth={1.5} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function ProcessCard({
  item,
  range,
  startsProcessDim,
  instancesProcessDim,
  durationMeasure,
}: {
  item: ProcessListItem;
  range: DashboardRange;
  startsProcessDim: string | undefined;
  instancesProcessDim: string | undefined;
  durationMeasure: string | undefined;
}) {
  const [spark, setSpark] = useState<SparkPoint[]>([]);
  const [p95, setP95] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const { from, to, grainMinutes } = range;
    const span = Math.max(1, to - from);
    const singleBucketGrain = Math.max(1, Math.ceil(span / 60_000));

    const sparkRequest =
      startsProcessDim != null
        ? api.tools.series({
            entity: "instance_starts",
            measure: "cnt",
            quantile: null,
            filters: { [startsProcessDim]: item.processId },
            from,
            to,
            grainMinutes,
          })
        : Promise.resolve({ ok: false as const, status: null, message: "no process dim" });

    const p95Request =
      instancesProcessDim != null && durationMeasure != null
        ? api.tools.series({
            entity: "instances",
            measure: null,
            quantile: 0.95,
            filters: { [instancesProcessDim]: item.processId },
            from,
            to,
            grainMinutes: singleBucketGrain,
          })
        : Promise.resolve({ ok: false as const, status: null, message: "no duration measure" });

    Promise.all([sparkRequest, p95Request]).then(([sparkResult, p95Result]) => {
      if (cancelled) {
        return;
      }
      setSpark(sparkResult.ok ? sparkResult.data.points.map((p) => ({ t: p.t, v: p.value })) : []);
      if (p95Result.ok && p95Result.data.points.length > 0) {
        const values = p95Result.data.points.map((p) => p.value);
        setP95(values.reduce((a, b) => a + b, 0) / values.length);
      } else {
        setP95(null);
      }
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.processId, range, startsProcessDim, instancesProcessDim, durationMeasure]);

  return (
    <Link to={`/processes/${encodeURIComponent(item.processId)}`} className="block">
      <Card className="transition-colors hover:bg-neutral-background-subtle">
        <CardContent className="flex flex-col gap-2 p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="truncate font-medium">{item.processId}</span>
            {item.hasDefinition ? (
              <Badge variant="neutral">v{item.latestVersion}</Badge>
            ) : (
              <Badge variant="warning" title="No process_definitions row -- diagram/version chip unavailable">
                no definition
              </Badge>
            )}
          </div>
          {loading ? (
            <span className="text-xs text-neutral-foreground-muted">Loading…</span>
          ) : (
            <>
              <Sparkline points={spark} />
              <span className="text-xs text-neutral-foreground-muted">
                p95 {p95 != null ? formatDuration(p95) : "–"}
              </span>
            </>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}

// ---------------------------------------------------------------------------------------------

export function ProcessListPage() {
  const { range } = useGlobalRange();
  const { entities } = useAppData();
  const [items, setItems] = useState<ProcessListItem[] | null>(null);
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

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const { from, to } = range;

    const definitionsRequest = processesApi.list();
    const metricsRequest =
      startsProcessDim != null
        ? api.tools.decompose({
            entity: "instance_starts",
            measure: "cnt",
            quantile: null,
            window: { from, to },
            baseline: { from, to },
            dim: startsProcessDim,
          })
        : Promise.resolve({ ok: false as const, status: null, message: "no process dim" });

    Promise.all([definitionsRequest, metricsRequest]).then(([definitionsResult, metricsResult]) => {
      if (cancelled) {
        return;
      }
      if (!definitionsResult.ok && !metricsResult.ok) {
        setError(definitionsResult.message);
        setItems(null);
        setLoading(false);
        return;
      }
      const definitions = definitionsResult.ok ? definitionsResult.data.definitions : [];
      const metricsProcessIds = metricsResult.ok
        ? [...metricsResult.data.rows].sort((a, b) => b.current - a.current).map((r) => r.value)
        : [];
      setItems(mergeProcessList(definitions, metricsProcessIds));
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range, startsProcessDim]);

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Processes</h1>
      {loading ? (
        <LoadingTile />
      ) : error ? (
        <EmptyTile heading="Not available yet" description={error} />
      ) : !items || items.length === 0 ? (
        <EmptyTile
          heading="No processes yet"
          description="Nothing in process_definitions and no instance_starts activity in this window."
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => (
            <ProcessCard
              key={item.processId}
              item={item}
              range={range}
              startsProcessDim={startsProcessDim}
              instancesProcessDim={instancesProcessDim}
              durationMeasure={durationMeasure}
            />
          ))}
        </div>
      )}
    </div>
  );
}
