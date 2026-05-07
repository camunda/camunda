/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type HeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

const SectionContext = React.createContext<HeadingLevel>(1);

function clamp(level: number): HeadingLevel {
  if (level < 1) return 1;
  if (level > 6) return 6;
  return level as HeadingLevel;
}

function useSectionLevel() {
  return React.useContext(SectionContext);
}

type SectionProps<E extends React.ElementType> = {
  as?: E;
  level?: HeadingLevel;
} & Omit<React.ComponentPropsWithoutRef<E>, 'as'>;

function Section<E extends React.ElementType = 'section'>({
  as,
  level: explicitLevel,
  className,
  children,
  ...props
}: SectionProps<E>) {
  const parentLevel = useSectionLevel();
  const level = clamp(explicitLevel ?? parentLevel + 1);
  const Comp = (as ?? 'section') as React.ElementType;
  return (
    <SectionContext.Provider value={level}>
      <Comp data-slot="section" className={cn(className)} {...props}>
        {children}
      </Comp>
    </SectionContext.Provider>
  );
}

export {Section, SectionContext, useSectionLevel};
export type {HeadingLevel, SectionProps};
