/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Badge } from "@camunda/design-system";

/** A finding's "rung" (an evidence-strength ladder -- rung 1 = weakest, higher = stronger) as a
 * color-coded badge. The contract only says "rung badge" without pinning down the scale's ends, so
 * this reads it as an open-ended integer and colors just the top band distinctly. */
export function RungBadge({ rung }: { rung: number }) {
  const variant = rung >= 3 ? "success" : rung === 2 ? "info" : "neutral";
  return <Badge variant={variant}>Rung {rung}</Badge>;
}
