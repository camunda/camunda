/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useCallback, useState} from 'react';

type DrdPanelState = 'closed' | 'maximized' | 'minimized';

function getInitialState(): DrdPanelState {
  const {drdPanelState} = getStateLocally('panelStates');
  return drdPanelState ?? 'minimized';
}

function useDrdPanelState(): [
  state: DrdPanelState,
  update: (state: DrdPanelState) => void,
] {
  const [state, setState] = useState(getInitialState);
  const update = useCallback((state: DrdPanelState) => {
    storeStateLocally({drdPanelState: state}, 'panelStates');
    setState(state);
  }, []);

  return [state, update];
}

function getDrdPanelWidth(): number | null {
  const {drdPanelWidth} = getStateLocally('panelStates');
  return drdPanelWidth ?? null;
}

function persistDrdPanelWidth(width: number): void {
  storeStateLocally({drdPanelWidth: width}, 'panelStates');
}

export {
  useDrdPanelState,
  getDrdPanelWidth,
  persistDrdPanelWidth,
  type DrdPanelState,
};
