/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {CodeSnippet as ShadcnCodeSnippet} from './code-snippet.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {CodeSnippetProps as CarbonCodeSnippetProps} from '@carbon/react';

export type CodeSnippetProps = CarbonCodeSnippetProps;

type CarbonOnlyKeys =
  | 'align'
  | 'autoAlign'
  | 'aria-label'
  | 'ariaLabel'
  | 'light'
  | 'maxExpandedNumberOfRows'
  | 'minCollapsedNumberOfRows'
  | 'minExpandedNumberOfRows';

function CodeSnippet(props: CodeSnippetProps & React.PropsWithChildren) {
  const {
    align,
    autoAlign,
    'aria-label': ariaLabel,
    ariaLabel: deprecatedAriaLabel,
    light,
    maxExpandedNumberOfRows,
    minCollapsedNumberOfRows,
    minExpandedNumberOfRows,
    ...rest
  } = props as CodeSnippetProps & Pick<Record<CarbonOnlyKeys, unknown>, CarbonOnlyKeys>;

  warnDroppedProps('CodeSnippet', {
    align,
    autoAlign,
    light,
    maxExpandedNumberOfRows,
    minCollapsedNumberOfRows,
    minExpandedNumberOfRows,
  });

  return (
    <ShadcnCodeSnippet
      aria-label={
        (ariaLabel as string | undefined) ??
        (deprecatedAriaLabel as string | undefined)
      }
      {...(rest as React.ComponentProps<typeof ShadcnCodeSnippet>)}
    />
  );
}

export {CodeSnippet};
