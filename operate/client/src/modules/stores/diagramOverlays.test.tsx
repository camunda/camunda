/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'modules/testing-library';
import {
  createStartEventOverlay,
  createTaskOverlay,
} from 'modules/mocks/diagramOverlays';
import {diagramOverlaysStore} from './diagramOverlays';

const OVERLAY_TYPE = 'myType';

describe('diagramOverlaysStore', () => {
  afterEach(() => {
    diagramOverlaysStore.reset();
  });

  it('should add and remove overlays ', async () => {
    const {container} = render(<div />);

    const startEventOverlay = createStartEventOverlay(container, OVERLAY_TYPE);
    const taskOverlay = createTaskOverlay(container, OVERLAY_TYPE);

    expect(diagramOverlaysStore.state.overlays).toEqual([]);

    diagramOverlaysStore.addOverlay(startEventOverlay);

    expect(diagramOverlaysStore.state.overlays).toEqual([startEventOverlay]);

    diagramOverlaysStore.addOverlay(taskOverlay);

    expect(diagramOverlaysStore.state.overlays).toEqual([
      startEventOverlay,
      taskOverlay,
    ]);

    diagramOverlaysStore.reset();

    expect(diagramOverlaysStore.state.overlays).toEqual([]);
  });
});
