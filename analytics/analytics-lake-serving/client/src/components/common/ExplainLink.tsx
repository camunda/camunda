/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Button } from "@camunda/design-system";
import { useExplainNavigate, type ExplainPrefill } from "../../lib/explainNav";

/** The "⌕" header action every dashboard tile carries: routes to Explain pre-filled with this
 * tile's own entity/measure/window, so "why did this move" is always one click away. */
export function ExplainLink({ prefill }: { prefill: ExplainPrefill }) {
  const goToExplain = useExplainNavigate();
  return (
    <Button
      size="icon-sm"
      variant="ghost"
      title="Explain this"
      aria-label="Explain this"
      onClick={() => goToExplain(prefill)}
    >
      ⌕
    </Button>
  );
}
