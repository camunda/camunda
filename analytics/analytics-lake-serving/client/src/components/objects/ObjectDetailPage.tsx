/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle } from "@camunda/design-system";
import {
  api,
  type JourneyActivity,
  type ObjectInstanceLink,
  type ObjectJourneyResponse,
  type ObjectRelation,
} from "../../lib/api";
import { formatDateTime, formatDuration } from "../../lib/format";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";
import { EgoGraphCard } from "./EgoGraphCard";
import { distinctElementCount, JourneyMiniMap, MAX_MINI_MAP_NODES } from "./JourneyMiniMap";

/** "Activity_ManualCreditReview" / "manual-credit-review" -> "Manual Credit Review": strips the
 * modeler's element-kind prefixes and turns the id's word breaks into a readable label. The raw id
 * stays available as the row's tooltip. */
function prettyElementName(elementId: string): string {
  const withoutPrefix = elementId.replace(
    /^(Activity|Event|Gateway|Flow|StartEvent|EndEvent|SubProcess|Task|ServiceTask|UserTask|CallActivity)_/i,
    "",
  );
  const spaced = withoutPrefix
    .replace(/[_-]+/g, " ")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .trim();
  return spaced.length === 0
    ? elementId
    : spaced.replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Last few digits of an instance key -- enough to tell instances apart without a wall of 16-digit
 * numbers; the full key stays in the tooltip. */
function shortKey(instanceKey: number): string {
  const s = String(instanceKey);
  return s.length <= 6 ? s : `…${s.slice(-6)}`;
}

interface Lane {
  instanceKey: number;
  processId: string;
  attributedVia: "ROOT" | "SCOPE";
  activities: JourneyActivity[];
}

/** Groups the flat activity list into per-instance "lanes", each lane's activities kept in start
 * order, and the lanes themselves ordered by their earliest activity. */
function groupByInstance(activities: JourneyActivity[]): Lane[] {
  const byInstance = new Map<number, JourneyActivity[]>();
  for (const a of activities) {
    const list = byInstance.get(a.instanceKey) ?? [];
    list.push(a);
    byInstance.set(a.instanceKey, list);
  }
  return [...byInstance.entries()]
    .map(([instanceKey, list]) => ({
      instanceKey,
      processId: list[0]?.processId ?? "",
      attributedVia: list[0]?.attributedVia ?? ("ROOT" as const),
      activities: [...list].sort(
        (x, y) => new Date(x.startedAt).getTime() - new Date(y.startedAt).getTime(),
      ),
    }))
    .sort(
      (a, b) =>
        new Date(a.activities[0].startedAt).getTime() -
        new Date(b.activities[0].startedAt).getTime(),
    );
}

/** The call-activity chain as a display order: parents before their children, then by first
 * activity time (falls back to lane order when there are no links). */
function laneDuration(lane: Lane): number | null {
  const start = new Date(lane.activities[0].startedAt).getTime();
  let end = Number.NEGATIVE_INFINITY;
  for (const a of lane.activities) {
    if (a.endedAt != null) {
      end = Math.max(end, new Date(a.endedAt).getTime());
    }
  }
  return Number.isFinite(end) ? end - start : null;
}

function RelationChip({
  type,
  id,
}: {
  type: string;
  id: string;
}) {
  return (
    <Link
      to={`/objects/${encodeURIComponent(type)}/${encodeURIComponent(id)}`}
      className="inline-flex items-center gap-1 rounded-full border border-border px-2.5 py-0.5 text-xs hover:bg-neutral-hover"
    >
      <span className="text-neutral-foreground-muted">{type}</span>
      <span className="font-mono font-medium">{id}</span>
    </Link>
  );
}

/** Relations split by direction relative to THIS object: what it contains vs. what it belongs
 * to -- each related object one click away. */
function RelationsCard({
  type,
  id,
  relations,
}: {
  type: string;
  id: string;
  relations: ObjectRelation[];
}) {
  const contains = relations.filter((r) => r.parentType === type && r.parentId === id);
  const partOf = relations.filter((r) => r.childType === type && r.childId === id);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Related objects</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {relations.length === 0 ? (
          <p className="text-sm text-neutral-foreground-muted">
            No related objects captured for this {type}.
          </p>
        ) : (
          <>
            {contains.length > 0 && (
              <div className="flex flex-col gap-1.5">
                <span className="text-xs font-medium text-neutral-foreground-muted">
                  Contains ({contains.length})
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {contains.map((r) => (
                    <RelationChip key={`${r.childType}:${r.childId}`} type={r.childType} id={r.childId} />
                  ))}
                </div>
              </div>
            )}
            {partOf.length > 0 && (
              <div className="flex flex-col gap-1.5">
                <span className="text-xs font-medium text-neutral-foreground-muted">
                  Part of ({partOf.length})
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {partOf.map((r) => (
                    <RelationChip key={`${r.parentType}:${r.parentId}`} type={r.parentType} id={r.parentId} />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

/** The call-activity chain between this object's instances, rendered as parent -> child hops with
 * the process names resolved from the journey's own lanes. */
function InstanceChainCard({
  links,
  lanes,
}: {
  links: ObjectInstanceLink[];
  lanes: Lane[];
}) {
  const processOf = new Map<number, string>(lanes.map((l) => [l.instanceKey, l.processId]));
  const label = (key: number) => {
    const process = processOf.get(key);
    return process ? `${process} ${shortKey(key)}` : shortKey(key);
  };
  return (
    <Card>
      <CardHeader>
        <CardTitle>Instance chain</CardTitle>
      </CardHeader>
      <CardContent>
        {links.length === 0 ? (
          <p className="text-sm text-neutral-foreground-muted">
            {lanes.length > 1
              ? "The touched instances are not linked by call activities."
              : "One instance touched this object; no call-activity chain."}
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {links.map((l, i) => (
              <li key={i} className="flex flex-wrap items-center gap-1.5 text-sm">
                <span title={String(l.parentInstanceKey)} className="font-medium">
                  {label(l.parentInstanceKey)}
                </span>
                <span className="text-neutral-foreground-muted">→</span>
                <span title={String(l.childInstanceKey)} className="font-medium">
                  {label(l.childInstanceKey)}
                </span>
                <span className="text-xs text-neutral-foreground-muted">
                  {l.linkType.replaceAll("_", " ").toLowerCase()}
                  {l.linkedAt != null ? ` · ${formatDateTime(l.linkedAt)}` : ""}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

const LANE_PREVIEW_ROWS = 8;

function JourneyLane({ lane }: { lane: Lane }) {
  const [expanded, setExpanded] = useState(false);
  const rows = expanded ? lane.activities : lane.activities.slice(0, LANE_PREVIEW_ROWS);
  const hidden = lane.activities.length - rows.length;
  const total = laneDuration(lane);
  return (
    <div className="flex flex-col gap-2 rounded border border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-semibold">{lane.processId}</span>
        <span
          title={String(lane.instanceKey)}
          className="font-mono text-xs text-neutral-foreground-muted"
        >
          {shortKey(lane.instanceKey)}
        </span>
        <Badge variant="neutral" title="How this instance's activities are attributed to the object: its whole run (root sighting) or just the sighted scope's subtree">
          {lane.attributedVia === "ROOT" ? "whole instance" : "sighted scope"}
        </Badge>
        <span className="ml-auto text-xs text-neutral-foreground-muted">
          {formatDateTime(lane.activities[0].startedAt)}
          {total != null ? ` · ${formatDuration(total)}` : ""}
        </span>
      </div>
      <ol className="flex flex-col">
        {rows.map((a, i) => (
          <li key={i} className="flex items-center gap-2 py-0.5 text-sm" title={a.elementId}>
            <span className="flex flex-col items-center self-stretch">
              <span className={`w-px flex-1 ${i === 0 ? "" : "bg-border"}`} />
              <span className="h-1.5 w-1.5 rounded-full bg-primary" />
              <span className={`w-px flex-1 ${i === rows.length - 1 && hidden === 0 ? "" : "bg-border"}`} />
            </span>
            <span className="flex-1 truncate">{prettyElementName(a.elementId)}</span>
            <span className="text-xs text-neutral-foreground-muted">
              {formatDateTime(a.startedAt)}
            </span>
            <span className="w-16 text-right text-xs tabular-nums text-neutral-foreground-muted">
              {a.durationMs != null ? formatDuration(a.durationMs) : "running"}
            </span>
          </li>
        ))}
      </ol>
      {hidden > 0 && (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="self-start text-xs text-primary underline"
        >
          Show {hidden} more activities
        </button>
      )}
    </div>
  );
}

type JourneyView = "timeline" | "map";

export function ObjectDetailPage() {
  const { type = "", id = "" } = useParams<{ type: string; id: string }>();
  const [journey, setJourney] = useState<ObjectJourneyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [journeyView, setJourneyView] = useState<JourneyView>("timeline");

  useEffect(() => {
    if (!type || !id) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.objects.journey({ type, id }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
        setJourney(null);
      } else {
        setJourney(result.data);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type, id]);

  const lanes = journey ? groupByInstance(journey.activities) : [];
  const firstSeen = journey?.sightings.find((s) => s.firstSeen != null)?.firstSeen ?? null;
  const elementCount = useMemo(() => (journey ? distinctElementCount(journey.activities) : 0), [journey]);
  const mapTooLarge = elementCount > MAX_MINI_MAP_NODES;
  const showMap = journeyView === "map" && !mapTooLarge;

  return (
    <div className="flex flex-col gap-4">
      <div>
        <Link to={`/objects/${encodeURIComponent(type)}`} className="text-xs text-primary underline">
          ← Back to {type}
        </Link>
        <h1 className="text-xl font-semibold">
          {type} · <span className="font-mono">{id}</span>
        </h1>
        {journey && (
          <p className="text-sm text-neutral-foreground-muted">
            Seen by {lanes.length} instance{lanes.length === 1 ? "" : "s"} ·{" "}
            {journey.activities.length} activities
            {firstSeen != null ? ` · first seen ${formatDateTime(firstSeen)}` : ""}
          </p>
        )}
      </div>

      {loading ? (
        <LoadingTile />
      ) : error || !journey ? (
        <EmptyTile heading="Not available yet" description={error ?? "No journey data."} />
      ) : (
        <>
          <EgoGraphCard type={type} id={id} />

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <RelationsCard type={type} id={id} relations={journey.relations} />
            <InstanceChainCard links={journey.links} lanes={lanes} />
          </div>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between gap-4">
              <CardTitle>Journey</CardTitle>
              <div className="flex gap-1">
                <Button
                  size="sm"
                  variant={journeyView === "timeline" ? "default" : "ghost"}
                  onClick={() => setJourneyView("timeline")}
                >
                  Timeline
                </Button>
                <Button
                  size="sm"
                  variant={journeyView === "map" ? "default" : "ghost"}
                  onClick={() => setJourneyView("map")}
                >
                  Map
                </Button>
              </div>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {lanes.length === 0 ? (
                <p className="text-sm text-neutral-foreground-muted">No activities recorded.</p>
              ) : showMap ? (
                <JourneyMiniMap activities={journey.activities} />
              ) : (
                <>
                  {journeyView === "map" && mapTooLarge && (
                    <p className="text-xs text-neutral-foreground-muted">
                      Too many elements ({elementCount}) for a readable map -- showing the timeline instead.
                    </p>
                  )}
                  {lanes.map((lane) => (
                    <JourneyLane key={lane.instanceKey} lane={lane} />
                  ))}
                </>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
