/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ListItem as ShadcnListItem,
  UnorderedList as ShadcnUnorderedList,
} from './unordered-list.shadcn';

import type {
  ListItemProps as CarbonListItemProps,
  UnorderedListProps as CarbonUnorderedListProps,
} from '@carbon/react';

export type UnorderedListProps = CarbonUnorderedListProps;
export type ListItemProps = CarbonListItemProps;

function UnorderedList(props: UnorderedListProps) {
  return <ShadcnUnorderedList {...props} />;
}

function ListItem(props: ListItemProps) {
  return <ShadcnListItem {...props} />;
}

export {ListItem, UnorderedList};
