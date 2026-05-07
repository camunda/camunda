/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `InlineLoading` swaps a status icon (active spinner / finished
 * check / error X / inactive) next to a description. shadcn ships only the
 * `Spinner`; the icon swap and layout are recreated inline here.
 */

import {CheckIcon, XIcon} from 'lucide-react';

import {Spinner} from '../spinner/spinner.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {InlineLoadingProps as CarbonInlineLoadingProps} from '@carbon/react';

export type InlineLoadingProps = CarbonInlineLoadingProps;

type CarbonInlineLoadingStatus =
  | 'inactive'
  | 'active'
  | 'finished'
  | 'error';

function StatusIcon({status}: {status: CarbonInlineLoadingStatus}) {
  if (status === 'active') return <Spinner className="size-4" />;
  if (status === 'finished')
    return <CheckIcon className="size-4 text-green-600" aria-hidden="true" />;
  if (status === 'error')
    return <XIcon className="size-4 text-destructive" aria-hidden="true" />;
  return null;
}

function InlineLoading(props: InlineLoadingProps) {
  const {
    className,
    description,
    iconDescription,
    onSuccess,
    status = 'active',
    successDelay,
    ...rest
  } = props;

  warnDroppedProps('InlineLoading', {
    iconDescription,
    onSuccess,
    successDelay,
  });

  return (
    <div
      className={cn('inline-flex items-center gap-2 text-sm', className)}
      role="status"
      {...rest}
    >
      <StatusIcon status={status as CarbonInlineLoadingStatus} />
      {description !== undefined ? <span>{description}</span> : null}
    </div>
  );
}

export {InlineLoading};
