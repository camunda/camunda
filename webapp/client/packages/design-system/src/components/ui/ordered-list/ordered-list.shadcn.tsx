/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type OrderedListProps = React.ComponentProps<'ol'> & {
  nested?: boolean;
  native?: boolean;
  isExpressive?: boolean;
};

function OrderedList({
  className,
  nested,
  native,
  isExpressive,
  ...rest
}: OrderedListProps) {
  return (
    <ol
      data-slot="ordered-list"
      data-nested={nested || undefined}
      data-expressive={isExpressive || undefined}
      className={cn(
        'list-decimal pl-6 text-sm text-foreground marker:text-muted-foreground',
        native ? 'list-decimal' : 'list-decimal',
        nested && 'mt-1',
        isExpressive && 'text-base leading-relaxed',
        '[&_li]:py-0.5',
        className,
      )}
      {...rest}
    />
  );
}

export {OrderedList};
export type {OrderedListProps};
