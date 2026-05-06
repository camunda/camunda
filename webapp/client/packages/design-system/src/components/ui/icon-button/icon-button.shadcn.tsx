/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `IconButton` is a square button containing only an icon, with a
 * built-in tooltip on hover/focus (via `label`). shadcn does not ship a
 * dedicated icon button — the canonical pattern is
 * `<Button variant="ghost" size="icon">` wrapped in `<Tooltip>` for the
 * accessible label. Both primitives live in their own folders and are
 * re-exported here for migration ergonomics.
 */

export {Button, buttonVariants} from '../button/button.shadcn';
export type {ButtonProps} from '../button/button.shadcn';
export {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../tooltip/tooltip.shadcn';
