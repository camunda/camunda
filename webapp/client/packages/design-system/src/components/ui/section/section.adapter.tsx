/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Section as ShadcnSection} from './section.shadcn';

import type {SectionProps as CarbonSectionProps} from '@carbon/react';

export type SectionProps<E extends React.ElementType = 'section'> =
  CarbonSectionProps<E>;

function Section<E extends React.ElementType = 'section'>(
  props: SectionProps<E>,
) {
  return <ShadcnSection {...(props as React.ComponentProps<typeof ShadcnSection>)} />;
}

export {Section};
