/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Tile` is a generic content container — basic, clickable,
 * selectable, expandable variants. shadcn's closest primitive is `Card`,
 * which is decomposed into `Card` / `CardHeader` / `CardTitle` /
 * `CardDescription` / `CardContent` / `CardFooter` / `CardAction`.
 * Canonical primitive lives at `card/card.shadcn.tsx` and is re-exported
 * here for migration ergonomics.
 */

export {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '../card/card.shadcn';
