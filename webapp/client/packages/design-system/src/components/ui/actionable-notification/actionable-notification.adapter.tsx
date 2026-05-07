/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `ActionableNotification` is a dual-mode component: when
 * `inline` is true (default) it renders an inline banner with a CTA
 * Button; otherwise it acts as a focus-trapping confirmation dialog.
 * shadcn has no single primitive for this — inline mode bridges to
 * `Alert` + `Button`, modal mode bridges to `AlertDialog`. Six Carbon
 * kinds collapse into shadcn's default/destructive Alert variants.
 */

import * as React from 'react';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../alert-dialog/alert-dialog.shadcn';
import {Alert, AlertDescription, AlertTitle} from '../alert/alert.shadcn';
import {Button} from '../button/button.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {ActionableNotificationProps as CarbonActionableNotificationProps} from '@carbon/react';

export type ActionableNotificationProps = CarbonActionableNotificationProps;

type CarbonNotificationKind =
  | 'error'
  | 'info'
  | 'info-square'
  | 'success'
  | 'warning'
  | 'warning-alt';

type AlertVariant = NonNullable<React.ComponentProps<typeof Alert>['variant']>;

const KIND_TO_VARIANT: Record<CarbonNotificationKind, AlertVariant> = {
  error: 'destructive',
  info: 'default',
  'info-square': 'default',
  success: 'default',
  warning: 'default',
  'warning-alt': 'default',
};

function ActionableNotification(props: ActionableNotificationProps) {
  const {
    actionButtonLabel,
    caption,
    children,
    className,
    closeOnEscape,
    hasFocus,
    hideCloseButton,
    inline = true,
    kind,
    lowContrast,
    onActionButtonClick,
    onClose,
    onCloseButtonClick,
    role,
    statusIconDescription,
    subtitle,
    title,
    'aria-label': ariaLabel,
    ...rest
  } = props as ActionableNotificationProps & {kind?: CarbonNotificationKind};

  warnDroppedProps('ActionableNotification', {
    caption,
    closeOnEscape,
    hasFocus,
    hideCloseButton,
    lowContrast,
    onClose,
    onCloseButtonClick,
    statusIconDescription,
  });

  const variant: AlertVariant = kind ? KIND_TO_VARIANT[kind] : 'default';

  if (!inline) {
    return (
      <AlertDialog open>
        <AlertDialogContent
          aria-label={ariaLabel}
          className={className}
          {...(rest as React.ComponentProps<typeof AlertDialogContent>)}
        >
          {title !== undefined ? (
            <AlertDialogHeader>
              <AlertDialogTitle>{title}</AlertDialogTitle>
            </AlertDialogHeader>
          ) : null}
          {subtitle !== undefined || children !== undefined ? (
            <AlertDialogDescription asChild>
              <div>
                {subtitle}
                {children}
              </div>
            </AlertDialogDescription>
          ) : null}
          {actionButtonLabel ? (
            <AlertDialogAction
              variant={kind === 'error' ? 'destructive' : 'default'}
              onClick={
                onActionButtonClick as React.MouseEventHandler<HTMLButtonElement>
              }
            >
              {actionButtonLabel}
            </AlertDialogAction>
          ) : null}
        </AlertDialogContent>
      </AlertDialog>
    );
  }

  return (
    <Alert
      variant={variant}
      className={className}
      role={role}
      aria-label={ariaLabel}
      {...rest}
    >
      {title !== undefined ? <AlertTitle>{title}</AlertTitle> : null}
      {subtitle !== undefined || children !== undefined || actionButtonLabel ? (
        <AlertDescription>
          {subtitle}
          {children}
          {actionButtonLabel ? (
            <Button
              variant="outline"
              size="sm"
              onClick={onActionButtonClick}
            >
              {actionButtonLabel}
            </Button>
          ) : null}
        </AlertDescription>
      ) : null}
    </Alert>
  );
}

export {ActionableNotification};
