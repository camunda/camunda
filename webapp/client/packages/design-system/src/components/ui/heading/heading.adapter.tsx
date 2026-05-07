/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type * as React from 'react';

import {Heading as ShadcnHeading} from './heading.shadcn';

// Carbon's `Heading` is a styled `h1` and does not export a public
// `HeadingProps` type, so we model the shape here.
export type HeadingProps = React.ComponentPropsWithoutRef<'h1'>;

function Heading(props: HeadingProps) {
  return <ShadcnHeading {...props} />;
}

export {Heading};
