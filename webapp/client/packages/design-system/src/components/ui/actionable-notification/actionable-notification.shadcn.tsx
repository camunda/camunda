/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `ActionableNotification` is an inline banner with a CTA button
 * (and optional dismiss). shadcn has no single primitive for this — the
 * vanilla migration target is `Alert` for inline (with a `Button` for the
 * CTA), or `AlertDialog` for modal-style confirmations. Both primitives
 * live in their own folders (`alert/` and `alert-dialog/`) and are
 * re-exported here for migration ergonomics.
 */

export {Alert, AlertDescription, AlertTitle} from '../alert/alert.shadcn';
export {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogOverlay,
  AlertDialogPortal,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '../alert-dialog/alert-dialog.shadcn';
