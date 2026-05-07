/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Link as ShadcnLink} from './link.shadcn';

import type {LinkProps as CarbonLinkProps} from '@carbon/react';

export type LinkProps<E extends React.ElementType = 'a'> = CarbonLinkProps<E>;

function Link<E extends React.ElementType = 'a'>(props: LinkProps<E>) {
  return <ShadcnLink {...(props as React.ComponentProps<typeof ShadcnLink>)} />;
}

export {Link};
