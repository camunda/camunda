/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Exhibit B, "Process page (diagram first)": version chip, BPMN heatmap + paths panel side by
 * side, KPI row, and the WIP-over-time tile. Owns the process's shared data fetches (the
 * activities/instance_variants stats, the definition XML) so the heatmap/paths/KPI-row children
 * don't each re-fetch the same thing.
 *
 * <h2>Version chip honesty</h2>
 *
 * Of every entity this page reads, only `instance_starts` declares a `version` dim (see the module
 * report) -- `activities`, `instances`, and `instance_variants` all group by `process_id` alone. So
 * the version chip here can only ever change ONE thing: which deployed BPMN XML is fetched and
 * rendered. Every stats-derived panel (heatmap coloring, paths, KPI row) necessarily aggregates
 * across every deployed version regardless of the chip -- each says so in its own caption rather
 * than silently implying a per-version breakdown that doesn't exist.
 */
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import { useAppData } from "../../lib/appData";
import { definitionsApi } from "../../lib/definitionsApi";
import { processesApi, type ProcessDefinitionSummary } from "../../lib/processesApi";
import { useGlobalRange } from "../../lib/rangeContext";
import { findDim } from "../../lib/registryHelpers";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";
import { PathsPanel } from "./PathsPanel";
import { ProcessHeatmapCard } from "./ProcessHeatmapCard";
import { ProcessKpiRow } from "./ProcessKpiRow";
import {
  fetchActivityStats,
  fetchPathStats,
  type ActivityStat,
  type PathStat,
} from "./processStats";
import { WipOverTimeTile } from "./WipOverTimeTile";

const PROCESS_ID_CANDIDATES = ["bpmnProcessId", "processId", "process"];

interface StatsState<T> {
  rows: T[];
  dim: string;
  processIdDim: string;
}

function toStatsState<T>(
  result: { ok: true; rows: T[]; dim: string; processIdDim: string } | { ok: false; message: string },
): StatsState<T> | null {
  return result.ok ? { rows: result.rows, dim: result.dim, processIdDim: result.processIdDim } : null;
}

export function ProcessDetailPage() {
  const { processId = "" } = useParams<{ processId: string }>();
  const { range } = useGlobalRange();
  const { entities } = useAppData();

  const [definitionsLoaded, setDefinitionsLoaded] = useState(false);
  const [summary, setSummary] = useState<ProcessDefinitionSummary | null>(null);
  const [version, setVersion] = useState<number | null>(null);

  const [bpmnXml, setBpmnXml] = useState<string | null>(null);
  const [xmlNotAvailable, setXmlNotAvailable] = useState(false);

  const [activities, setActivities] = useState<StatsState<ActivityStat> | null | undefined>(undefined);
  const [activitiesError, setActivitiesError] = useState<string | null>(null);
  const [paths, setPaths] = useState<StatsState<PathStat> | null | undefined>(undefined);
  const [pathsError, setPathsError] = useState<string | null>(null);

  const startsProcessDim = findDim(
    entities.find((e) => e.name === "instance_starts"),
    PROCESS_ID_CANDIDATES,
  );
  const instancesProcessDim = findDim(
    entities.find((e) => e.name === "instances"),
    PROCESS_ID_CANDIDATES,
  );

  // The process's definition summary (versions + latest), used to default/populate the version
  // chip -- looked up once per processId from the same list the picker page calls.
  useEffect(() => {
    let cancelled = false;
    setDefinitionsLoaded(false);
    processesApi.list().then((result) => {
      if (cancelled) {
        return;
      }
      const found = result.ok ? result.data.definitions.find((d) => d.processId === processId) : undefined;
      setSummary(found ?? null);
      setVersion(found ? found.latestVersion : null);
      setDefinitionsLoaded(true);
    });
    return () => {
      cancelled = true;
    };
  }, [processId]);

  // The selected version's BPMN XML, refetched whenever the chip changes.
  useEffect(() => {
    if (version == null) {
      setBpmnXml(null);
      setXmlNotAvailable(true);
      return;
    }
    let cancelled = false;
    setBpmnXml(null);
    setXmlNotAvailable(false);
    definitionsApi.get(processId, version).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.ok) {
        setBpmnXml(result.data.bpmnXml);
      } else {
        setXmlNotAvailable(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [processId, version]);

  // Element and path stats -- version-independent (see class javadoc), refetched on range change.
  useEffect(() => {
    if (entities.length === 0) {
      return;
    }
    let cancelled = false;
    setActivities(undefined);
    setActivitiesError(null);
    fetchActivityStats(entities, processId, range).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setActivitiesError(result.message);
        setActivities(null);
      } else {
        setActivities(toStatsState(result));
      }
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [processId, range, entities.length]);

  useEffect(() => {
    if (entities.length === 0) {
      return;
    }
    let cancelled = false;
    setPaths(undefined);
    setPathsError(null);
    fetchPathStats(entities, processId, range).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setPathsError(result.message);
        setPaths(null);
      } else {
        setPaths(toStatsState(result));
      }
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [processId, range, entities.length]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <Link to="/processes" className="text-sm text-primary underline">
              ← Processes
            </Link>
          </div>
          <h1 className="text-xl font-semibold">{processId}</h1>
        </div>
        <div className="flex flex-col items-end gap-1">
          {!definitionsLoaded ? (
            <span className="text-sm text-neutral-foreground-muted">Loading versions…</span>
          ) : summary ? (
            <Select value={String(version ?? summary.latestVersion)} onValueChange={(v) => setVersion(Number(v))}>
              <SelectTrigger size="sm" className="w-28">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {summary.versions.map((v) => (
                  <SelectItem key={v.version} value={String(v.version)}>
                    v{v.version}
                    {v.version === summary.latestVersion ? " (latest)" : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <span
              className="rounded border border-border px-3 py-1.5 text-sm text-neutral-foreground-muted"
              title="No process_definitions row for this process id"
            >
              no definition
            </span>
          )}
          <span className="max-w-64 text-right text-xs text-neutral-foreground-muted">
            Only changes the diagram below -- element/path stats have no version dimension yet.
          </span>
        </div>
      </div>

      {summary ? (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <ProcessHeatmapCard
              processId={processId}
              version={version ?? summary.latestVersion}
              bpmnXml={bpmnXml}
              xmlNotAvailable={xmlNotAvailable}
              stats={activities === undefined ? null : (activities?.rows ?? [])}
            />
          </div>
          <div className="flex flex-col gap-2 rounded border border-border p-3">
            <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
              Paths
            </span>
            <PathsPanel
              processId={processId}
              stats={paths?.rows ?? null}
              loading={paths === undefined}
              error={pathsError}
              from={range.from}
              to={range.to}
              processIdDim={paths?.processIdDim}
              variantDim={paths?.dim}
            />
          </div>
        </div>
      ) : definitionsLoaded ? (
        <div className="flex flex-col gap-2 rounded border border-border p-3">
          <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
            Paths (no deployed definition found -- leading with paths, per the design's fallback)
          </span>
          <PathsPanel
            processId={processId}
            stats={paths?.rows ?? null}
            loading={paths === undefined}
            error={pathsError}
            from={range.from}
            to={range.to}
            processIdDim={paths?.processIdDim}
            variantDim={paths?.dim}
          />
        </div>
      ) : (
        <LoadingTile />
      )}

      <ProcessKpiRow
        processId={processId}
        range={range}
        startsProcessDim={startsProcessDim}
        instancesProcessDim={instancesProcessDim}
        activities={activities ?? null}
        paths={paths ?? null}
      />

      {activitiesError && !activities ? (
        <EmptyTile heading="Element stats not available yet" description={activitiesError} />
      ) : null}

      <WipOverTimeTile processId={processId} from={range.from} to={range.to} grainMinutes={range.grainMinutes} />
    </div>
  );
}
