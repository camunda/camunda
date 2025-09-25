/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DecisionInstanceState} from '@camunda/camunda-api-zod-schemas/8.8';
import {useMemo, useState} from 'react';

interface DecisionStateOverlay {
  state: DecisionInstanceState;
  decisionDefinitionId: string;
  container: HTMLDivElement;
}

interface DecisionStateOverlayActions {
  replaceOverlays(overlays: DecisionStateOverlay[]): void;
  addOverlay(overlay: DecisionStateOverlay): void;
  clearOverlays(): void;
}

function useDrdStateOverlay(): [
  state: DecisionStateOverlay[],
  actions: DecisionStateOverlayActions,
] {
  const [state, setState] = useState<DecisionStateOverlay[]>([]);

  const actions = useMemo<DecisionStateOverlayActions>(
    () => ({
      replaceOverlays: (overlays) => setState(overlays),
      addOverlay: (overlay) => setState((prev) => [...prev, overlay]),
      clearOverlays: () => setState([]),
    }),
    [],
  );

  return [state, actions];
}

export {
  useDrdStateOverlay,
  type DecisionStateOverlay,
  type DecisionStateOverlayActions,
};
