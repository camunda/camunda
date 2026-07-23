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

const NAV_ITEMS = [
  { to: "/dashboards", label: "Dashboards" },
  { to: "/explain", label: "Explain" },
  { to: "/objects", label: "Objects" },
  { to: "/data", label: "Data" },
];

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return isActive
    ? "border-b-2 border-primary px-1 pb-2 text-sm font-medium text-neutral-foreground"
    : "border-b-2 border-transparent px-1 pb-2 text-sm text-neutral-foreground-muted hover:text-neutral-foreground";
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

/** The app shell: header with the primary nav and the perspective switcher, and the routed page
 * body below. */
export function Shell() {
  return (
    <div className="min-h-full bg-neutral-background-subtle text-neutral-foreground">
      <header className="border-b border-border bg-neutral-background">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-6 py-4">
          <div className="flex items-center justify-between gap-4">
            <div className="flex flex-col gap-1">
              <span className="text-lg font-semibold">Camunda · Analytics Lake</span>
              <span className="text-xs text-neutral-foreground-muted">
                Read-only serving over the lake warehouse
              </span>
            </div>
            <PerspectiveSwitcher />
          </div>
          <nav className="flex gap-6">
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
