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
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';

describe('<Bar />', () => {
  afterEach(() => {
    flowNodeTimeStampStore.reset();
  });

  it('should show an icon based on node type and the node name', () => {
    // @ts-expect-error ts-migrate(2741) FIXME: Property 'typeDetails' is missing in type '{ activ... Remove this comment to see the full error message
    render(<Bar node={mockStartNode} isSelected={false} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByTestId(`flow-node-icon-${mockStartNode.type}`)
    ).toBeInTheDocument();
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'name' does not exist on type '{ activity... Remove this comment to see the full error message
    expect(screen.getByText(mockStartNode.name)).toBeInTheDocument();
  });

  it('should show the correct name for multi instance nodes', () => {
    // @ts-expect-error ts-migrate(2322) FIXME: Type '{ activityId: string; children: never[]; end... Remove this comment to see the full error message
    render(<Bar node={mockMultiInstanceBody} isSelected={false} />, {
      wrapper: ThemeProvider,
    });

    expect(
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'name' does not exist on type '{ activity... Remove this comment to see the full error message
      screen.getByText(`${mockMultiInstanceBody.name} (Multi Instance)`)
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', () => {
    // @ts-expect-error ts-migrate(2322) FIXME: Type '{ activityId: string; children: never[]; end... Remove this comment to see the full error message
    render(<Bar node={mockMultiInstanceBody} isSelected={false} />, {
      wrapper: ThemeProvider,
    });

    expect(screen.queryByText('12 Dec 2018 00:00:00')).not.toBeInTheDocument();

    flowNodeTimeStampStore.toggleTimeStampVisibility();

    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
  });
});
