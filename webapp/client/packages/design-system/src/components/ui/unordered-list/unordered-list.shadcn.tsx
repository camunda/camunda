/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type UnorderedListProps = React.ComponentPropsWithoutRef<'ul'> & {
  nested?: boolean;
  isExpressive?: boolean;
};

function UnorderedList({
  className,
  nested,
  isExpressive,
  ...props
}: UnorderedListProps) {
  return (
    <ul
      data-slot="unordered-list"
      data-nested={nested ? 'true' : undefined}
      data-expressive={isExpressive ? 'true' : undefined}
      className={cn(
        'list-disc pl-5 text-sm leading-6 marker:text-muted-foreground',
        nested && 'mt-1 mb-0 ml-4',
        isExpressive && 'text-base leading-7',
        className,
      )}
      {...props}
    />
  );
}

type ListItemProps = React.ComponentPropsWithoutRef<'li'>;

function ListItem({className, ...props}: ListItemProps) {
  return (
    <li
      data-slot="list-item"
      className={cn('mt-1 first:mt-0', className)}
      {...props}
    />
  );
}

export {UnorderedList, ListItem};
export type {UnorderedListProps, ListItemProps};
