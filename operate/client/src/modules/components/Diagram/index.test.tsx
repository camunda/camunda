/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {observer} from 'mobx-react';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {createPortal} from 'react-dom';
import {Diagram} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const OVERLAY_TYPE = 'myType';

const Overlay: React.FC<{container: HTMLElement; data: React.ReactNode}> =
  observer(({container, data}) => {
    return createPortal(<div>{data}</div>, container);
  });

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

describe('<Diagram />', () => {
  afterEach(() => {
    diagramOverlaysStore.reset();
  });

  it('should render diagram controls', async () => {
    render(<Diagram xml={'<bpmn:definitions/>'} />, {wrapper: Wrapper});

    expect(await screen.findByText('Diagram mock')).toBeInTheDocument();

    expect(
      await screen.findByRole('button', {name: 'Reset diagram zoom'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom in diagram'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom out diagram'}),
    ).toBeInTheDocument();
  });

  it('should render overlays', async () => {
    const DiagramComponent = () => (
      <Diagram
        xml={'<bpmn:definitions/>'}
        overlaysData={[
          {
            payload: 'example data',
            type: OVERLAY_TYPE,
            flowNodeId: 'startEvent_1',
            position: {top: 0, left: 0},
          },
        ]}
      >
        {diagramOverlaysStore.state.overlays.map(
          ({container, payload, flowNodeId}) => (
            <Overlay
              key={flowNodeId}
              container={container}
              data={payload as React.ReactNode}
            />
          ),
        )}
      </Diagram>
    );

    const {rerender} = render(<DiagramComponent />, {wrapper: Wrapper});

    await waitFor(() => diagramOverlaysStore.state.overlays.length > 0);

    rerender(<DiagramComponent />);

    expect(await screen.findByText(/example data/)).toBeInTheDocument();
  });
});
