/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {TimeStampLabel} from './index';

describe('TimeStampLabel', () => {
  it('should hide/display time stamp on time stamp toggle', () => {
    render(
      <TimeStampLabel
        isSelected={false}
        timeStamp={'2020-07-09T12:26:22.237+0000'}
      />,
      {wrapper: ThemeProvider}
    );
    expect(screen.queryByText('12 Dec 2018 00:00:00')).not.toBeInTheDocument();
    flowNodeTimeStampStore.toggleTimeStampVisibility();
    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
    flowNodeTimeStampStore.toggleTimeStampVisibility();
    expect(screen.queryByText('12 Dec 2018 00:00:00')).not.toBeInTheDocument();
  });
});
