/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

type Overlay = {
  payload: unknown;
  container: HTMLElement;
  flowNodeId: string;
  type: string;
};

type State = {
  overlays: Overlay[];
};

const DEFAULT_STATE: State = {
  overlays: [],
};

class DiagramOverlays {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  addOverlay = (overlay: Overlay) => {
    this.state.overlays.push(overlay);
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const diagramOverlaysStore = new DiagramOverlays();
