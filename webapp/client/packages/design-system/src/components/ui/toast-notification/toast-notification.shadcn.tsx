/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `ToastNotification` is a self-rendering banner managed
 * imperatively or by an external store. shadcn's migration target is
 * `Sonner` — a toast queue + viewport rendered once near the app root,
 * with a `toast(...)` imperative API. The canonical primitive lives at
 * `sonner/sonner.shadcn.tsx` (exports `Toaster` + `toast`) and is
 * re-exported here for migration ergonomics.
 */

export {Toaster, toast} from '../sonner/sonner.shadcn';
