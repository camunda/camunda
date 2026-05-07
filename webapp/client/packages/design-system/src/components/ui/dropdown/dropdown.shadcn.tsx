/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Dropdown` is a "single-select listbox with custom UI" — closer
 * to shadcn's `Select` than to shadcn's `DropdownMenu` (which is the
 * action-menu primitive used by `MenuButton`/`OverflowMenu`).
 *
 * The canonical primitive lives at `select/select.shadcn.tsx` and is
 * re-exported here for migration ergonomics.
 */

export {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectScrollDownButton,
  SelectScrollUpButton,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from '../select/select.shadcn';
