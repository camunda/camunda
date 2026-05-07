/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Loading` is the page-level loading indicator — a larger
 * spinner with optional full-page overlay (`withOverlay`), inline-size
 * mode (`small`), and an `active` toggle. shadcn ships only the bare
 * `Spinner`; sizing, overlay, and visibility are app composition.
 * The adapter renders the spinner unconditionally when active and warns
 * on the rest.
 */

import * as React from 'react';

import {Spinner} from '../spinner/spinner.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {LoadingProps as CarbonLoadingProps} from '@carbon/react';

export type LoadingProps = CarbonLoadingProps;

function Loading(props: LoadingProps) {
  const {
    active,
    className,
    description,
    id,
    small,
    withOverlay,
    ...rest
  } = props;

  warnDroppedProps('Loading', {
    description,
    id,
    small,
    withOverlay,
  });

  if (active === false) return null;

  return (
    <Spinner
      className={className}
      {...(rest as React.ComponentProps<typeof Spinner>)}
    />
  );
}

export {Loading};
