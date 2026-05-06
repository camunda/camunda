/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Modal`, `ComposedModal`, `ModalHeader`, `ModalBody`,
 * `ModalFooter` map to shadcn's `Dialog` family. The canonical primitive
 * lives at `dialog/dialog.shadcn.tsx` and is re-exported here for
 * migration ergonomics.
 *
 * Note: for confirmation modals (the "are you sure?" pattern with
 * primary/cancel buttons and destructive emphasis), use shadcn
 * `AlertDialog` instead — see `alert-dialog/alert-dialog.shadcn.tsx`.
 */

export {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogOverlay,
  DialogPortal,
  DialogTitle,
  DialogTrigger,
} from '../dialog/dialog.shadcn';
