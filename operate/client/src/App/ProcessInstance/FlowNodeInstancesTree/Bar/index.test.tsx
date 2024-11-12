/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, render, screen} from 'modules/testing-library';
import {Bar} from './index';
import {mockStartNode, mockStartEventBusinessObject} from './index.setup';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => flowNodeTimeStampStore.reset, []);

  return <>{children}</>;
};

describe('<Bar />', () => {
  it('should show the node name and an icon based on node state', () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('ACTIVE-icon')).toBeInTheDocument();
    expect(
      screen.getByText(mockStartEventBusinessObject.name),
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', async () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
        isTimestampLabelVisible
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(await screen.findByText(MOCK_TIMESTAMP)).toBeInTheDocument();
  });

  it('should show latest successful migration date', async () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
        isRoot
        latestMigrationDate={MOCK_TIMESTAMP}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getByText(`Migrated 2018-12-12 00:00:00`),
    ).toBeInTheDocument();
  });

  it('should not show latest successful migration date for non-root', async () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
        latestMigrationDate={MOCK_TIMESTAMP}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByText(`Migrated 2018-12-12 00:00:00`),
    ).not.toBeInTheDocument();
  });
});
