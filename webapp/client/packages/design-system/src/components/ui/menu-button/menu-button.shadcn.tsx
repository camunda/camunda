/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `MenuButton` + `MenuItem` is "a labelled button that opens an
 * action menu". The shadcn migration target is `DropdownMenu` — a
 * Radix-based action-menu primitive (NOT shadcn `Select`, which is for
 * single-select listboxes; that maps to Carbon `Dropdown`).
 *
 * The canonical primitive lives at `dropdown-menu/dropdown-menu.shadcn.tsx`
 * and is re-exported here. The trigger is typically a `Button` from the
 * `button/` folder — also re-exported for convenience.
 */

export {Button, buttonVariants} from '../button/button.shadcn';
export {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuPortal,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '../dropdown-menu/dropdown-menu.shadcn';
