/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Skeleton} from './index';

describe('<Skeleton />', () => {
  it('should render the correct amount of rows', () => {
    const rowCount = 10;
    // @ts-expect-error ts-migrate(2322) FIXME: Property 'rowsToDisplay' does not exist on type 'I... Remove this comment to see the full error message
    render(<Skeleton rowsToDisplay={10} />, {wrapper: ThemeProvider});

    expect(
      screen.getAllByTestId('flow-node-instance-log-skeleton-row')
    ).toHaveLength(rowCount);
  });
});
