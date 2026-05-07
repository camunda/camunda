/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `InlineNotification` is a dismissible inline banner with status
 * icon, title, subtitle, and a close button. shadcn's `Alert` ships only
 * default + destructive — kind=error maps to destructive; everything else
 * collapses to default. The close button, status icon, and dismissibility
 * are app composition territory — they're dropped here.
 */

import * as React from 'react';

import {Alert, AlertDescription, AlertTitle} from '../alert/alert.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {InlineNotificationProps as CarbonInlineNotificationProps} from '@carbon/react';

export type InlineNotificationProps = CarbonInlineNotificationProps;

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

function InlineNotification(props: InlineNotificationProps) {
  const {
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
    title,
    'aria-label': ariaLabel,
    ...rest
  } = props as InlineNotificationProps & {kind?: CarbonNotificationKind};

  warnDroppedProps('InlineNotification', {
    hideCloseButton,
    lowContrast,
    onClose,
    onCloseButtonClick,
    statusIconDescription,
  });

  const variant: AlertVariant = kind ? KIND_TO_VARIANT[kind] : 'default';

  return (
    <Alert
      variant={variant}
      className={className}
      role={role}
      aria-label={ariaLabel}
      {...rest}
    >
      {title !== undefined ? <AlertTitle>{title}</AlertTitle> : null}
      {subtitle !== undefined || children !== undefined ? (
        <AlertDescription>
          {subtitle}
          {children}
        </AlertDescription>
      ) : null}
    </Alert>
  );
}

export {InlineNotification};
