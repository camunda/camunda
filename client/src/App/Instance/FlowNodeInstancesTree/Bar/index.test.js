/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Bar} from './index';
import {mockStartNode, mockMultiInstanceBody} from './index.setup';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';

describe('<Bar />', () => {
  afterEach(() => {
    flowNodeTimeStamp.reset();
  });

  it('should show an icon based on node type and the node name', () => {
    render(<Bar node={mockStartNode} isSelected={false} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByTestId(`flow-node-icon-${mockStartNode.type}`)
    ).toBeInTheDocument();
    expect(screen.getByText(mockStartNode.name)).toBeInTheDocument();
  });

  it('should show the correct name for multi instance nodes', () => {
    render(<Bar node={mockMultiInstanceBody} isSelected={false} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByText(`${mockMultiInstanceBody.name} (Multi Instance)`)
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', () => {
    render(<Bar node={mockMultiInstanceBody} isSelected={false} />, {
      wrapper: ThemeProvider,
    });

    expect(screen.queryByText('12 Dec 2018 00:00:00')).not.toBeInTheDocument();

    flowNodeTimeStamp.toggleTimeStampVisibility();

    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
  });
});
