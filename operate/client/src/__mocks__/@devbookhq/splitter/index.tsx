/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

const SplitDirection = {
  Horizontal: 'horizontal',
  Vertical: 'vertical',
} as const;

type SplitterProps = {
  children: React.ReactNode;
  direction: (typeof SplitDirection)[keyof typeof SplitDirection];
  classes?: string[];
  minHeights?: number[];
  minWidths?: number[];
  initialSizes?: number[];
  gutterClassName?: string;
  draggerClassName?: string;
  onResizeStarted?: () => void;
  onResizeFinished?: (pairIdx: number, newSizes: number[]) => void;
};

const Splitter: React.FC<SplitterProps> = ({children}) => {
  return <div data-testid="splitter-mock">{children}</div>;
};

export default Splitter;

export {SplitDirection};
