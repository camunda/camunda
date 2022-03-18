/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';

type Overlay = {
  payload: unknown;
  container: HTMLElement;
  flowNodeId: string;
};

type State = {
  overlays: {[type: string]: Overlay[] | undefined};
};

const DEFAULT_STATE: State = {
  overlays: {},
};

class DiagramOverlays {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  addOverlay = (type: string, overlay: Overlay) => {
    if (this.state.overlays[type] === undefined) {
      this.state.overlays[type] = [];
    }

    this.state.overlays[type]!.push(overlay);
  };

  removeOverlay = (type: string, flowNodeId: string) => {
    if (this.state.overlays[type] === undefined) {
      return;
    }

    const index = this.state.overlays[type]!.findIndex(
      (overlay) => overlay.flowNodeId === flowNodeId
    );

    if (index >= 0) {
      this.state.overlays[type]!.splice(index, 1);
    }

    if (this.state.overlays[type]!.length === 0) {
      delete this.state.overlays[type];
    }
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const diagramOverlaysStore = new DiagramOverlays();
