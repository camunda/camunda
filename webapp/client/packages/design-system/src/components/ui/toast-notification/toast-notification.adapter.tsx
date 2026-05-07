/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `ToastNotification` is a self-rendering banner — consumers
 * mount it as JSX and it disappears on its own (or via `onClose`).
 * shadcn's `sonner` is imperative: a single `<Toaster />` lives near the
 * app root, and `toast.<kind>(...)` enqueues toasts. To keep declarative
 * call sites compiling during the migration, this adapter renders `null`
 * and dispatches a sonner toast on mount; cleanup dismisses the toast
 * on unmount. Consumers should migrate to imperative `toast(...)` calls.
 */

import * as React from 'react';

import {toast} from '../sonner/sonner.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {ToastNotificationProps as CarbonToastNotificationProps} from '@carbon/react';

export type ToastNotificationProps = CarbonToastNotificationProps;

type CarbonNotificationKind =
  | 'error'
  | 'info'
  | 'info-square'
  | 'success'
  | 'warning'
  | 'warning-alt';

type SonnerKind = 'error' | 'success' | 'warning' | 'info';

const KIND_TO_SONNER: Record<CarbonNotificationKind, SonnerKind> = {
  error: 'error',
  success: 'success',
  warning: 'warning',
  'warning-alt': 'warning',
  info: 'info',
  'info-square': 'info',
};

function ToastNotification(props: ToastNotificationProps) {
  const {
    caption,
    children,
    className,
    hideCloseButton,
    kind,
    lowContrast,
    onClose,
    onCloseButtonClick,
    role,
    statusIconDescription,
    subtitle,
    timeout,
    title,
    'aria-label': ariaLabel,
  } = props as ToastNotificationProps & {kind?: CarbonNotificationKind};

  warnDroppedProps('ToastNotification', {
    caption,
    children,
    className,
    hideCloseButton,
    lowContrast,
    onCloseButtonClick,
    role,
    statusIconDescription,
    ariaLabel,
  });

  React.useEffect(() => {
    const sonnerKind: SonnerKind = kind ? KIND_TO_SONNER[kind] : 'info';
    const id = toast[sonnerKind](title ?? '', {
      description: subtitle,
      duration: timeout,
      onDismiss: onClose
        ? () =>
            (onClose as (event: React.MouseEvent) => void)(
              {} as React.MouseEvent<HTMLElement>,
            )
        : undefined,
    });
    return () => {
      toast.dismiss(id);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return null;
}

export {ToastNotification};
