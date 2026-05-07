/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Header as ShadcnHeader} from './header.shadcn';

import type {HeaderProps as CarbonHeaderProps} from '@carbon/react';

export type HeaderProps = CarbonHeaderProps;

function Header(props: HeaderProps) {
  return (
    <ShadcnHeader {...(props as React.ComponentProps<typeof ShadcnHeader>)} />
  );
}

export {Header};
