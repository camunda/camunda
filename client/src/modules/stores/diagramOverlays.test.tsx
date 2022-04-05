/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render} from '@testing-library/react';
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

    const startEventOverlay = createStartEventOverlay(container);
    const taskOverlay = createTaskOverlay(container);

    expect(diagramOverlaysStore.state.overlays).toEqual({});

    diagramOverlaysStore.addOverlay(OVERLAY_TYPE, startEventOverlay);

    expect(diagramOverlaysStore.state.overlays).toEqual({
      [OVERLAY_TYPE]: [startEventOverlay],
    });

    diagramOverlaysStore.addOverlay(OVERLAY_TYPE, taskOverlay);

    expect(diagramOverlaysStore.state.overlays).toEqual({
      [OVERLAY_TYPE]: [startEventOverlay, taskOverlay],
    });

    diagramOverlaysStore.removeOverlay(
      OVERLAY_TYPE,
      startEventOverlay.flowNodeId
    );

    expect(diagramOverlaysStore.state.overlays).toEqual({
      [OVERLAY_TYPE]: [taskOverlay],
    });

    diagramOverlaysStore.removeOverlay(OVERLAY_TYPE, taskOverlay.flowNodeId);

    expect(diagramOverlaysStore.state.overlays).toEqual({});
  });
});
