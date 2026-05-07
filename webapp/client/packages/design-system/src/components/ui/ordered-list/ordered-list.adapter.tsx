/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {OrderedList as ShadcnOrderedList} from './ordered-list.shadcn';

import type {OrderedListProps as CarbonOrderedListProps} from '@carbon/react';

export type OrderedListProps = CarbonOrderedListProps;

function OrderedList(props: OrderedListProps) {
  const {className, nested, native, isExpressive, children, ...rest} =
    props as OrderedListProps & {
      className?: string;
      nested?: boolean;
      native?: boolean;
      isExpressive?: boolean;
      children?: React.ReactNode;
    };

  return (
    <ShadcnOrderedList
      className={className}
      nested={nested}
      native={native}
      isExpressive={isExpressive}
      {...(rest as React.HTMLAttributes<HTMLOListElement>)}
    >
      {children}
    </ShadcnOrderedList>
  );
}

export {OrderedList};
