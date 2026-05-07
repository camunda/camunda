/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type LayerLevel = 0 | 1 | 2;

const LayerContext = React.createContext<{level: LayerLevel}>({level: 0});

function useLayer() {
  return React.useContext(LayerContext);
}

const BACKGROUND_BY_LEVEL: Record<LayerLevel, string> = {
  0: 'bg-background',
  1: 'bg-card',
  2: 'bg-muted',
};

type LayerProps<E extends React.ElementType> = {
  as?: E;
  level?: LayerLevel;
  withBackground?: boolean;
} & Omit<React.ComponentPropsWithoutRef<E>, 'as'>;

function Layer<E extends React.ElementType = 'div'>({
  as,
  level: explicitLevel,
  withBackground = false,
  className,
  children,
  ...props
}: LayerProps<E>) {
  const parent = useLayer();
  const next: LayerLevel =
    explicitLevel !== undefined
      ? explicitLevel
      : ((Math.min(parent.level + 1, 2) as LayerLevel));
  const Comp = (as ?? 'div') as React.ElementType;
  return (
    <LayerContext.Provider value={{level: next}}>
      <Comp
        data-slot="layer"
        data-layer-level={next}
        className={cn(withBackground && BACKGROUND_BY_LEVEL[next], className)}
        {...props}
      >
        {children}
      </Comp>
    </LayerContext.Provider>
  );
}

export {Layer, LayerContext, useLayer};
export type {LayerProps, LayerLevel};
