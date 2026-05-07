/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Callout` packages title/subtitle/action button into a single
 * monolithic component. shadcn's `Alert` is composed: AlertTitle and
 * AlertDescription wrap the children, and the action button is rendered
 * inline inside the description as a regular Button. Carbon ships 6 kinds
 * (info/warning/error/success/info-square/warning-alt) but shadcn's Alert
 * only models default vs. destructive — non-error kinds collapse to default.
 */

import * as React from 'react';

import {Button} from '../button/button.shadcn';
import {Alert, AlertDescription, AlertTitle} from '../alert/alert.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {CalloutProps as CarbonCalloutProps} from '@carbon/react';

export type CalloutProps = CarbonCalloutProps;

type CarbonCalloutKind =
  | 'error'
  | 'info'
  | 'info-square'
  | 'success'
  | 'warning'
  | 'warning-alt';

type AlertVariant = NonNullable<React.ComponentProps<typeof Alert>['variant']>;

const KIND_TO_VARIANT: Record<CarbonCalloutKind, AlertVariant> = {
  error: 'destructive',
  info: 'default',
  'info-square': 'default',
  success: 'default',
  warning: 'default',
  'warning-alt': 'default',
};

function Callout(props: CalloutProps) {
  const {
    actionButtonLabel,
    children,
    className,
    kind,
    lowContrast,
    onActionButtonClick,
    statusIconDescription,
    subtitle,
    title,
    titleId,
    ...rest
  } = props as CalloutProps & {kind?: CarbonCalloutKind};

  warnDroppedProps('Callout', {
    lowContrast,
    statusIconDescription,
  });

  const variant: AlertVariant = kind ? KIND_TO_VARIANT[kind] : 'default';

  return (
    <Alert variant={variant} className={className} {...rest}>
      {title !== undefined ? (
        <AlertTitle id={titleId}>{title}</AlertTitle>
      ) : null}
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

export {Callout};
