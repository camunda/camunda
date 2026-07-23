/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
import { Button, Card, CardContent, CardHeader, CardTitle } from "@camunda/design-system";
import { api, type CohortCompareRequest, type CohortCompareRow, type Finding } from "../../lib/api";
import { BoldNumbers } from "../common/BoldNumbers";
import { confidenceLabel, describeFinding, TOOL_LABELS } from "../../lib/findingText";
import { formatCount, formatLift, formatPercent } from "../../lib/format";
import { EvidencePanel } from "./EvidencePanel";
import { NumbersTable } from "./NumbersTable";
import { RungBadge } from "./RungBadge";

/** A finding whose evidence would require a full table scan is returned deferred rather than
 * computed eagerly -- the contract names this kind explicitly ("SCAN_DEFERRED") as the one finding
 * that renders differently from the claim-sentence cards: an estimated cost and a manual trigger. */
function ScanDeferredBody({ finding }: { finding: Finding }) {
  const [rows, setRows] = useState<CohortCompareRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const estimatedRows =
    typeof finding.numbers.estimatedRows === "number" ? finding.numbers.estimatedRows : undefined;

  const runScan = () => {
    if (finding.tool !== "cohort-compare") {
      setError(`Can't run scan: finding is bound to tool "${finding.tool}", not cohort-compare.`);
      return;
    }
    setLoading(true);
    setError(null);
    api.tools
      .cohortCompare(finding.toolParams as unknown as CohortCompareRequest)
      .then((result) => {
        if (!result.ok) {
          setError(result.message);
        } else {
          setRows(result.data.rows);
        }
        setLoading(false);
      });
  };

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-neutral-foreground-muted">
        This finding needs a full scan to confirm
        {estimatedRows != null ? ` (~${formatCount(estimatedRows)} rows)` : ""}. It hasn't been run
        yet.
      </p>
      <div>
        <Button size="sm" onClick={runScan} disabled={loading}>
          {loading ? "Scanning…" : "Run scan"}
        </Button>
      </div>
      {error ? <p className="text-sm text-destructive-foreground">{error}</p> : null}
      {rows ? <LiftTable rows={rows} /> : null}
    </div>
  );
}

/** Shared with CohortComparePanel's own rendering -- see that file for the "protective rows styled
 * distinctly" convention (lift < 1 = protective/greener, lift > 1 = risk/redder). */
export function LiftTable({ rows }: { rows: CohortCompareRow[] }) {
  if (rows.length === 0) {
    return <p className="text-sm text-neutral-foreground-muted">No attributes cleared the support floor.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-border text-left text-neutral-foreground-muted">
            <th className="py-2 pr-4 font-medium">Attribute</th>
            <th className="py-2 pr-4 font-medium">Bucket</th>
            <th className="py-2 pr-4 text-right font-medium">Slow share</th>
            <th className="py-2 pr-4 text-right font-medium">Fast share</th>
            <th className="py-2 pr-4 text-right font-medium">Lift</th>
            <th className="py-2 text-right font-medium">Support (slow / fast)</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => {
            const protective = r.lift < 1;
            return (
              <tr
                key={`${r.attribute}-${r.bucket}-${i}`}
                className={`border-b border-border/60 ${protective ? "bg-green-50 dark:bg-green-950/20" : ""}`}
              >
                <td className="py-2 pr-4 font-medium">{r.attribute}</td>
                <td className="py-2 pr-4 font-mono text-xs">{r.bucket}</td>
                <td className="py-2 pr-4 text-right tabular-nums">{formatPercent(r.slowShare)}</td>
                <td className="py-2 pr-4 text-right tabular-nums">{formatPercent(r.fastShare)}</td>
                <td
                  className={`py-2 pr-4 text-right tabular-nums font-medium ${
                    protective ? "text-green-700 dark:text-green-400" : "text-destructive-foreground"
                  }`}
                >
                  {formatLift(r.lift)}
                </td>
                <td className="py-2 text-right tabular-nums text-neutral-foreground-muted">
                  {formatCount(r.slowN)} / {formatCount(r.fastN)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/**
 * One ranked finding as an asserted sentence, per the design sketch's Exhibit C: the claim leads
 * (numbers bolded, via {@link BoldNumbers}), a quiet meta line names the underlying tool + a
 * confidence word derived from the rung, and the actual chart/table sits behind a single "see
 * evidence" click -- {@link EvidencePanel} reuses the same seven tool panels the Advanced section
 * shows, prefilled and auto-run from this finding's own `toolParams`. "Numbers"/"SQL" stay as a
 * quieter secondary escape hatch for the raw claim/numbers bag and the generated SQL.
 */
export function FindingCard({
  finding,
  onEditRerun,
}: {
  finding: Finding;
  onEditRerun: (finding: Finding) => void;
}) {
  const [showEvidence, setShowEvidence] = useState(false);
  const [showNumbers, setShowNumbers] = useState(false);
  const [showSql, setShowSql] = useState(false);
  const isDeferred = finding.kind === "SCAN_DEFERRED";

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-3">
          <div className="flex flex-col gap-1">
            <CardTitle className="text-base font-normal">
              {isDeferred ? "Deferred finding" : <BoldNumbers text={describeFinding(finding)} />}
            </CardTitle>
            {!isDeferred ? (
              <span className="text-xs text-neutral-foreground-muted">
                {TOOL_LABELS[finding.tool]} · {confidenceLabel(finding.rung)}
              </span>
            ) : null}
          </div>
          <RungBadge rung={finding.rung} />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {isDeferred ? <ScanDeferredBody finding={finding} /> : null}

        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="secondary" onClick={() => setShowEvidence((s) => !s)}>
            {showEvidence ? "Hide evidence" : "See evidence"}
          </Button>
          <Button size="sm" variant="secondary" onClick={() => onEditRerun(finding)}>
            Edit & rerun
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setShowNumbers((s) => !s)}>
            {showNumbers ? "Hide numbers" : "Numbers"}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setShowSql((s) => !s)}>
            {showSql ? "Hide SQL" : "SQL"}
          </Button>
        </div>

        {showEvidence ? <EvidencePanel finding={finding} /> : null}
        {showNumbers ? <NumbersTable numbers={finding.numbers} /> : null}
        {showSql ? (
          <pre className="overflow-x-auto rounded bg-neutral-background-subtle p-3 text-xs">
            {finding.sql}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}
