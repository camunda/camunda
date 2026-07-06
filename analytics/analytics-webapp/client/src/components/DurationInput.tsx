/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useMemo } from "react";
import {
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";

const UNITS: { id: string; label: string; ms: number }[] = [
  { id: "s", label: "seconds", ms: 1_000 },
  { id: "min", label: "minutes", ms: 60_000 },
  { id: "h", label: "hours", ms: 3_600_000 },
  { id: "d", label: "days", ms: 86_400_000 },
];

/** Picks the coarsest unit that represents the given ms as a whole number. */
function splitMs(ms: number): { amount: number; unitId: string } {
  for (const u of [...UNITS].reverse()) {
    if (ms >= u.ms && ms % u.ms === 0) {
      return { amount: ms / u.ms, unitId: u.id };
    }
  }
  return { amount: Math.round(ms / 60_000), unitId: "min" };
}

/** A friendly duration picker: a magnitude plus a unit, emitting a value in milliseconds. */
export function DurationInput({
  valueMs,
  onChange,
}: {
  valueMs: number;
  onChange: (ms: number) => void;
}) {
  const { amount, unitId } = useMemo(() => splitMs(valueMs), [valueMs]);
  const unit = UNITS.find((u) => u.id === unitId) ?? UNITS[1];

  return (
    <div className="flex items-center gap-2">
      <Input
        className="w-24"
        type="number"
        min={0}
        value={String(amount)}
        onChange={(e) => {
          const n = Number(e.target.value);
          onChange(Number.isFinite(n) && n >= 0 ? n * unit.ms : 0);
        }}
      />
      <Select value={unit.id} onValueChange={(id) => onChange(amount * (UNITS.find((u) => u.id === id)?.ms ?? 60_000))}>
        <SelectTrigger className="w-32">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {UNITS.map((u) => (
            <SelectItem key={u.id} value={u.id}>
              {u.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
