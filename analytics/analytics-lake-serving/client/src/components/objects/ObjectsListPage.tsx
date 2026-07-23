/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@camunda/design-system";
import { api, type ObjectRow, type ObjectStatus } from "../../lib/api";
import { useAppData } from "../../lib/appData";
import { formatCount, formatDateTime, formatDuration } from "../../lib/format";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";

const PAGE_SIZE = 50;

function ageLabel(row: ObjectRow): string {
  const end = row.closedAt != null ? new Date(row.closedAt).getTime() : Date.now();
  return formatDuration(end - new Date(row.firstSeen).getTime());
}

export function ObjectsListPage() {
  const { type = "" } = useParams<{ type: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { objectTypes, objectTypesLoaded, closedSupported } = useAppData();

  const status = (searchParams.get("status") as ObjectStatus | null) ?? "ALL";
  const [offset, setOffset] = useState(0);
  const [rows, setRows] = useState<ObjectRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => setOffset(0), [type, status]);

  useEffect(() => {
    if (!type) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.objects.list({ type, status, limit: PAGE_SIZE, offset }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
      } else {
        setRows(result.data.rows);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type, status, offset]);

  if (objectTypesLoaded && objectTypes.length === 0) {
    return (
      <EmptyTile
        heading="No object types discovered"
        description="GET /api/objects/types returned none -- object perspectives aren't available yet."
      />
    );
  }

  if (!type) {
    return (
      <EmptyTile heading="Pick an object type" description="Choose one from the selector above." />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-xl font-semibold">Objects · {type}</h1>
        <div className="flex items-center gap-2">
          <Select value={type} onValueChange={(v) => navigate(`/objects/${encodeURIComponent(v)}`)}>
            <SelectTrigger size="sm" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {objectTypes.map((t) => (
                <SelectItem key={t} value={t}>
                  {t}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={status}
            onValueChange={(v) => setSearchParams({ status: v })}
          >
            <SelectTrigger size="sm" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="OPEN">Open</SelectItem>
              {closedSupported ? <SelectItem value="CLOSED">Closed</SelectItem> : null}
              <SelectItem value="ALL">All</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {loading ? (
        <LoadingTile />
      ) : error ? (
        <EmptyTile heading="Not available yet" description={error} />
      ) : !rows || rows.length === 0 ? (
        <EmptyTile heading="No objects" description={`No ${status.toLowerCase()} ${type} objects.`} />
      ) : (
        <Table size="sm">
          <TableHeader>
            <TableRow>
              <TableHead>Object</TableHead>
              <TableHead>First seen</TableHead>
              <TableHead>Age</TableHead>
              <TableHead className="text-right">Instances</TableHead>
              <TableHead>Closed at</TableHead>
              <TableHead>Outcome</TableHead>
              <TableHead className="text-right">Duration</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.objectId}>
                <TableCell>
                  <Link
                    to={`/objects/${encodeURIComponent(type)}/${encodeURIComponent(r.objectId)}`}
                    className="font-mono text-xs text-primary underline"
                  >
                    {r.objectId}
                  </Link>
                </TableCell>
                <TableCell>{formatDateTime(r.firstSeen)}</TableCell>
                <TableCell>{ageLabel(r)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.nInstances)}</TableCell>
                <TableCell>{r.closedAt != null ? formatDateTime(r.closedAt) : "–"}</TableCell>
                <TableCell>{r.outcome ?? "–"}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {r.durationMs != null ? formatDuration(r.durationMs) : "–"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <div className="flex items-center justify-between text-sm text-neutral-foreground-muted">
        <span>
          Showing {offset + 1}–{offset + (rows?.length ?? 0)}
        </span>
        <div className="flex gap-2">
          <button
            className="disabled:opacity-40"
            disabled={offset === 0}
            onClick={() => setOffset((o) => Math.max(0, o - PAGE_SIZE))}
          >
            ← Prev
          </button>
          <button
            className="disabled:opacity-40"
            disabled={!rows || rows.length < PAGE_SIZE}
            onClick={() => setOffset((o) => o + PAGE_SIZE)}
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  );
}
