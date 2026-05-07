/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

const GridContext = React.createContext<{columns: number; condensed: boolean}>({
  columns: 16,
  condensed: false,
});

type GridProps<E extends React.ElementType> = {
  as?: E;
  columns?: number;
  condensed?: boolean;
  narrow?: boolean;
  fullWidth?: boolean;
} & Omit<React.ComponentPropsWithoutRef<E>, 'as'>;

function Grid<E extends React.ElementType = 'div'>({
  as,
  columns = 16,
  condensed = false,
  narrow = false,
  fullWidth = false,
  className,
  style,
  ...props
}: GridProps<E>) {
  const Comp = (as ?? 'div') as React.ElementType;
  const gap = condensed ? '0.0625rem' : narrow ? '1rem' : '2rem';
  return (
    <GridContext.Provider value={{columns, condensed}}>
      <Comp
        data-slot="grid"
        data-condensed={condensed || undefined}
        data-narrow={narrow || undefined}
        className={cn('grid w-full', fullWidth && 'max-w-none', className)}
        style={{
          gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
          gap,
          ...style,
        }}
        {...props}
      />
    </GridContext.Provider>
  );
}

function useGrid() {
  return React.useContext(GridContext);
}

export {Grid, GridContext, useGrid};
export type {GridProps};
