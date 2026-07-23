/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import { NavLink, Outlet } from "react-router";
import { PROCESSES_PERSPECTIVE, useAppData } from "../../lib/appData";
import { useGlobalRange } from "../../lib/rangeContext";
import { DashboardRangePicker } from "../dashboards/DashboardRangePicker";

// Nav labels per the design sketch's information architecture; `to` targets stay on the paths the
// backend's SpaForwardController already forwards wherever a new one hasn't landed yet (Today ->
// /dashboards, Ask why -> /explain -- see the doc comment on App.tsx). Processes is a new
// destination without a backend-forwarded path yet (/processes); flagged in the lane report.
const NAV_ITEMS = [
  { to: "/dashboards", label: "Today" },
  { to: "/processes", label: "Processes" },
  { to: "/objects", label: "Objects" },
  { to: "/explain", label: "Ask why" },
  { to: "/data", label: "Data" },
];

// The sketch's navbar (Exhibit A): a continuous structural underline the whole strip sits on, with
// the active item's own accent-colored underline drawn on top of it (`-mb-px` pulls each link's own
// border down onto the nav's shared one instead of doubling it).
function navLinkClass({ isActive }: { isActive: boolean }): string {
  return isActive
    ? "-mb-px border-b-2 border-primary px-1 pb-2 text-sm font-medium text-neutral-foreground"
    : "-mb-px border-b-2 border-transparent px-1 pb-2 text-sm text-neutral-foreground-muted hover:text-neutral-foreground";
}

/** The global perspective switcher: "processes" or one of the discovered object types. Object
 * perspectives are hidden entirely when GET /api/objects/types failed or returned none, per the
 * spec -- the switcher then silently degrades to a fixed "Processes" label. */
function PerspectiveSwitcher() {
  const { perspective, setPerspective, objectTypes, objectTypesLoaded } = useAppData();

  if (objectTypesLoaded && objectTypes.length === 0) {
    return (
      <span className="rounded border border-border px-3 py-1.5 text-sm text-neutral-foreground-muted">
        Processes
      </span>
    );
  }

  return (
    <Select value={perspective} onValueChange={setPerspective}>
      <SelectTrigger size="sm" className="w-44">
        <SelectValue placeholder="Perspective" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={PROCESSES_PERSPECTIVE}>Processes</SelectItem>
        {objectTypes.map((t) => (
          <SelectItem key={t} value={t}>
            {t}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/** The one global time-range control (design sketch: "Time range -- one picker in the header,
 * shared by Today/Processes/Objects"). Consumed via context, so every page that reads it re-renders
 * on a change without prop-drilling the range down through the router. */
function GlobalRangePicker() {
  const { range, setRange } = useGlobalRange();
  return <DashboardRangePicker selected={range.label} onSelect={setRange} />;
}

/** The app shell: header with the primary nav, the perspective switcher, and the global time range,
 * and the routed page body below. */
export function Shell() {
  return (
    <div className="min-h-full bg-neutral-background-subtle text-neutral-foreground">
      <header className="border-b border-border bg-neutral-background">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-6 py-4">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-col gap-1">
              <span className="text-lg font-semibold">Camunda · Analytics Lake</span>
              <span className="text-xs text-neutral-foreground-muted">
                Read-only serving over the lake warehouse
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <GlobalRangePicker />
              <PerspectiveSwitcher />
            </div>
          </div>
          <nav className="flex gap-6 border-b border-border">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} className={navLinkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-6 py-6">
        <Outlet />
      </main>
    </div>
  );
}
