/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';

import {ThemeProvider} from 'modules/contexts/ThemeContext';

import {TimeStampPill} from './index';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';

jest.mock('modules/utils/bpmn');

describe('TimeStampPill', () => {
  beforeEach(() => {
    createMockDataManager();
  });

  it('should render "Show" / "Hide" label', () => {
    render(
      <ThemeProvider>
        <DataManagerProvider>
          <TimeStampPill />
        </DataManagerProvider>
      </ThemeProvider>
    );

    expect(screen.getByText('Show End Time')).toBeInTheDocument();
    flowNodeTimeStamp.toggleTimeStampVisibility();
    expect(screen.getByText('Hide End Time')).toBeInTheDocument();
  });
});
