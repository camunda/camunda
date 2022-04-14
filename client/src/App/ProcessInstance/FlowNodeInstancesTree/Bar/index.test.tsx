/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Bar} from './index';
import {
  mockStartNode,
  mockStartMetaData,
  mockMultiInstanceBodyNode,
  mockMultiInstanceBodyMetaData,
} from './index.setup';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';

describe('<Bar />', () => {
  afterEach(() => {
    flowNodeTimeStampStore.reset();
  });

  it('should show an icon based on node type and the node name', () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        metaData={mockStartMetaData}
        isBold={false}
        isSelected={false}
        hasTopBorder={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByTestId(`flow-node-icon-${mockStartMetaData.type.elementType}`)
    ).toBeInTheDocument();
    expect(screen.getByText(mockStartMetaData.name)).toBeInTheDocument();
  });

  it('should show the correct name for multi instance nodes', () => {
    render(
      <Bar
        flowNodeInstance={mockMultiInstanceBodyNode}
        metaData={mockMultiInstanceBodyMetaData}
        isBold={false}
        isSelected={false}
        hasTopBorder={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText(`${mockMultiInstanceBodyMetaData.name} (Multi Instance)`)
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', () => {
    render(
      <Bar
        flowNodeInstance={mockMultiInstanceBodyNode}
        metaData={mockMultiInstanceBodyMetaData}
        isBold={false}
        isSelected={false}
        hasTopBorder={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.queryByText(mockMultiInstanceBodyNode.endDate!)
    ).not.toBeInTheDocument();

    flowNodeTimeStampStore.toggleTimeStampVisibility();

    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
  });
});
