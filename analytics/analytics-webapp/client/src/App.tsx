/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import { Dashboard } from "./components/Dashboard";
import { RangePicker } from "./components/RangePicker";
import { api, type TimeRange } from "./lib/api";

export default function App() {
  const [processes, setProcesses] = useState<string[]>([]);
  const [tenants, setTenants] = useState<string[]>([]);
  const [process, setProcess] = useState<string>("");
  const [tenant, setTenant] = useState<string>("");
  const [rangeLabel, setRangeLabel] = useState<string>("All");
  const [range, setRange] = useState<TimeRange | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.processes(), api.tenants()])
      .then(([ps, ts]) => {
        setProcesses(ps);
        setTenants(ts);
        if (ps.length) {
          setProcess(ps[0]);
        }
        setTenant(ts.length ? ts[0] : "<default>");
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  return (
    <div className="min-h-full bg-neutral-background-subtle text-neutral-foreground">
      <header className="border-b border-border bg-neutral-background">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-6 py-4">
          <div className="mr-auto flex flex-col">
            <span className="text-lg font-semibold">Camunda · Process Analytics</span>
            <span className="text-xs text-neutral-foreground-muted">
              Pre-aggregated metrics from the streaming pipeline
            </span>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <span className="text-neutral-foreground-muted">Process</span>
            <Select value={process} onValueChange={setProcess}>
              <SelectTrigger className="w-56">
                <SelectValue placeholder="Select a process" />
              </SelectTrigger>
              <SelectContent>
                {processes.map((p) => (
                  <SelectItem key={p} value={p}>
                    {p}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>

          <label className="flex items-center gap-2 text-sm">
            <span className="text-neutral-foreground-muted">Tenant</span>
            <Select value={tenant} onValueChange={setTenant}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="Tenant" />
              </SelectTrigger>
              <SelectContent>
                {tenants.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>

          <label className="flex items-center gap-2 text-sm">
            <span className="text-neutral-foreground-muted">Range</span>
            <RangePicker
              selected={rangeLabel}
              onSelect={(label, r) => {
                setRangeLabel(label);
                setRange(r);
              }}
            />
          </label>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-6 py-6">
        {error ? (
          <p className="text-destructive-foreground">Failed to load: {error}</p>
        ) : process ? (
          <Dashboard process={process} tenant={tenant || "<default>"} range={range} />
        ) : (
          <p className="text-neutral-foreground-muted">
            No analytics data yet. Run the pipeline (or start with <code>-Danalytics.seed=true</code>) and
            reload.
          </p>
        )}
      </main>
    </div>
  );
}
