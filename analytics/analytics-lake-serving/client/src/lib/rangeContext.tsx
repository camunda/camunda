/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { defaultRange, type DashboardRange } from "./range";

interface RangeContextValue {
  range: DashboardRange;
  setRange: (r: DashboardRange) => void;
}

const RangeContext = createContext<RangeContextValue | null>(null);

/**
 * The one global time range, picked in the shared header (see Shell.tsx) and consumed by Today,
 * Processes, and (eventually) Objects -- the design sketch's "two global controls" section. Pages
 * that don't use a time window (Ask why has its own per-question window; Data is raw SQL) simply
 * don't call {@link useGlobalRange}.
 */
export function RangeProvider({ children }: { children: ReactNode }) {
  const [range, setRange] = useState<DashboardRange>(defaultRange());
  const value = useMemo(() => ({ range, setRange }), [range]);
  return <RangeContext.Provider value={value}>{children}</RangeContext.Provider>;
}

export function useGlobalRange(): RangeContextValue {
  const ctx = useContext(RangeContext);
  if (!ctx) {
    throw new Error("useGlobalRange must be used within RangeProvider");
  }
  return ctx;
}
