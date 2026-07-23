/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { objectsStatsApi } from "../../lib/objectsStatsApi";

export type ClosingBehavior = "loading" | "closes" | "never-closes";

/**
 * Whether an object type is ever observed closing at all, e.g. a "customer" that simply has no
 * closing rule (every customer stays "open" forever, which reads as confusing rather than
 * informative -- see the Today object-type cards and the Objects list page's status filter).
 *
 * Heuristic, not a real capability flag: POST /api/objects/stats for the type, and treat an empty
 * `outcomes` list as "this type has never been observed closing". This is a stand-in for a proper
 * declaration-derived "has a closing rule" flag the backend doesn't expose yet -- a type that
 * simply hasn't closed *yet* in the current data (but has a closing rule) would read the same way.
 * Flagged here rather than baked in silently so the next lane that touches object declarations
 * knows to replace it.
 */
export function useClosingBehavior(type: string): ClosingBehavior {
  const [behavior, setBehavior] = useState<ClosingBehavior>("loading");

  useEffect(() => {
    if (!type) {
      return;
    }
    let cancelled = false;
    setBehavior("loading");
    objectsStatsApi.stats({ type }).then((result) => {
      if (cancelled) {
        return;
      }
      // On failure, default to "closes" -- the safer assumption is to keep showing the
      // OPEN/CLOSED filter and "open" wording rather than silently hiding a real capability.
      setBehavior(result.ok && result.data.outcomes.length === 0 ? "never-closes" : "closes");
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  return behavior;
}
