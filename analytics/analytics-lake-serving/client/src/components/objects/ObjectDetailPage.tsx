/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { Badge, Card, CardContent, CardHeader, CardTitle } from "@camunda/design-system";
import { api, type JourneyActivity, type ObjectJourneyResponse } from "../../lib/api";
import { formatCount, formatDateTime, formatDuration } from "../../lib/format";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";

/** Groups the flat activity list into per-instance "lanes", each lane's activities kept in start
 * order, and the lanes themselves ordered by their earliest activity -- a swimlane-ish vertical
 * grouping without any BPMN rendering (out of scope for this lane; see the module report). */
function groupByInstance(activities: JourneyActivity[]): { instanceKey: string; processId: string; activities: JourneyActivity[] }[] {
  const byInstance = new Map<string, JourneyActivity[]>();
  for (const a of activities) {
    const list = byInstance.get(a.instanceKey) ?? [];
    list.push(a);
    byInstance.set(a.instanceKey, list);
  }
  return [...byInstance.entries()]
    .map(([instanceKey, list]) => ({
      instanceKey,
      processId: list[0]?.processId ?? "",
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

export function ObjectDetailPage() {
  const { type = "", id = "" } = useParams<{ type: string; id: string }>();
  const [journey, setJourney] = useState<ObjectJourneyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

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
      } else {
        setJourney(result.data);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type, id]);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <Link to={`/objects/${encodeURIComponent(type)}`} className="text-xs text-primary underline">
          ← Back to {type}
        </Link>
        <h1 className="text-xl font-semibold">
          {type} · <span className="font-mono">{id}</span>
        </h1>
      </div>

      {loading ? (
        <LoadingTile />
      ) : error || !journey ? (
        <EmptyTile heading="Not available yet" description={error ?? "No journey data."} />
      ) : (
        <>
          <p className="text-sm text-neutral-foreground-muted">
            {formatCount(journey.sightings)} sighting(s) across {formatCount(journey.activities.length)}{" "}
            activities
          </p>

          <Card>
            <CardHeader>
              <CardTitle>Journey</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {journey.activities.length === 0 ? (
                <p className="text-sm text-neutral-foreground-muted">No activities recorded.</p>
              ) : (
                groupByInstance(journey.activities).map((lane) => (
                  <div key={lane.instanceKey} className="flex flex-col gap-2 rounded border border-border p-3">
                    <div className="flex items-center justify-between">
                      <span className="font-mono text-xs font-medium">{lane.instanceKey}</span>
                      <span className="text-xs text-neutral-foreground-muted">{lane.processId}</span>
                    </div>
                    <div className="flex flex-col gap-1.5">
                      {lane.activities.map((a, i) => (
                        <div key={i} className="flex items-center gap-2 text-sm">
                          <Badge variant={a.attributedVia === "ROOT" ? "primary" : "neutral"}>
                            {a.attributedVia}
                          </Badge>
                          <span className="flex-1 truncate">{a.elementId}</span>
                          <span className="text-xs text-neutral-foreground-muted">
                            {formatDateTime(a.startedAt)}
                          </span>
                          <span className="w-16 text-right text-xs tabular-nums text-neutral-foreground-muted">
                            {a.durationMs != null ? formatDuration(a.durationMs) : "running"}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                ))
              )}
            </CardContent>
          </Card>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Relations</CardTitle>
              </CardHeader>
              <CardContent>
                {journey.relations.length === 0 ? (
                  <p className="text-sm text-neutral-foreground-muted">No related objects.</p>
                ) : (
                  <ul className="flex flex-col gap-1">
                    {journey.relations.map((r, i) => (
                      <li key={i}>
                        {r.type ? (
                          <Link
                            to={`/objects/${encodeURIComponent(r.type)}/${encodeURIComponent(r.objectId)}`}
                            className="font-mono text-xs text-primary underline"
                          >
                            {r.type} · {r.objectId}
                          </Link>
                        ) : (
                          <span className="font-mono text-xs">{r.objectId}</span>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Instance links</CardTitle>
              </CardHeader>
              <CardContent>
                {journey.links.length === 0 ? (
                  <p className="text-sm text-neutral-foreground-muted">No linked instances.</p>
                ) : (
                  <ul className="flex flex-col gap-1">
                    {journey.links.map((l, i) => (
                      <li key={i} className="font-mono text-xs">
                        {l.instanceKey}
                        {l.processId ? ` (${l.processId})` : ""}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
