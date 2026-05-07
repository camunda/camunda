/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  ContainedList as ShadcnContainedList,
  ContainedListItem as ShadcnContainedListItem,
} from './contained-list.shadcn';

import type {ContainedListProps as CarbonContainedListProps} from '@carbon/react';

export type ContainedListProps = CarbonContainedListProps;

// Carbon does not export a public `ContainedListItemProps` type, so we model
// the same shape the primitive accepts (Carbon's internal interface mirrors
// these fields).
export type ContainedListItemProps = {
  action?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  disabled?: boolean;
  onClick?: () => void;
  renderIcon?: React.ComponentType<{className?: string}>;
};

function ContainedList(props: ContainedListProps) {
  return (
    <ShadcnContainedList
      {...(props as React.ComponentProps<typeof ShadcnContainedList>)}
    />
  );
}

function ContainedListItem(props: ContainedListItemProps) {
  return <ShadcnContainedListItem {...props} />;
}

export {ContainedList, ContainedListItem};
