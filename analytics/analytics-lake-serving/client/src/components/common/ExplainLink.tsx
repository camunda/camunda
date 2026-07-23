/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Button } from "@camunda/design-system";
import { useExplainNavigate, type ExplainPrefill } from "../../lib/explainNav";

/** The "why?" header action every dashboard tile carries: routes to Ask why pre-filled with this
 * tile's own entity/measure/filters/window, so "why did this move" is always one click away (see
 * the design sketch's "every number can be asked why" principle). Visible label per that sketch --
 * previously an icon-only "⌕" glyph with no on-screen text. */
export function ExplainLink({ prefill }: { prefill: ExplainPrefill }) {
  const goToExplain = useExplainNavigate();
  return (
    <Button
      size="sm"
      variant="ghost"
      title="Ask why this moved"
      aria-label="Ask why this moved"
      onClick={() => goToExplain(prefill)}
    >
      why?
    </Button>
  );
}
