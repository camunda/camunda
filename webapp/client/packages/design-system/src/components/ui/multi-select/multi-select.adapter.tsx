/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {MultiSelect as ShadcnMultiSelect} from './multi-select.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {MultiSelectProps as CarbonMultiSelectProps} from '@carbon/react';

export type MultiSelectProps<ItemType> = CarbonMultiSelectProps<ItemType>;

function MultiSelect<ItemType>(props: MultiSelectProps<ItemType>) {
  const {
    autoAlign,
    clearAnnouncement,
    decorator,
    direction,
    downshiftProps,
    light,
    locale,
    selectionFeedback,
    slug,
    sortItems,
    compareItems,
    translateWithId,
    type,
    useTitleInItem,
    warn,
    warnText,
    ...rest
  } = props as MultiSelectProps<ItemType> & {
    sortItems?: unknown;
    compareItems?: unknown;
    translateWithId?: unknown;
  };

  warnDroppedProps('MultiSelect', {
    autoAlign,
    clearAnnouncement,
    decorator,
    direction,
    downshiftProps,
    light,
    locale,
    selectionFeedback,
    slug,
    sortItems,
    compareItems,
    translateWithId,
    type,
    useTitleInItem,
    warn,
    warnText,
  });

  return (
    <ShadcnMultiSelect<ItemType>
      {...(rest as React.ComponentProps<typeof ShadcnMultiSelect<ItemType>>)}
    />
  );
}

export {MultiSelect};
