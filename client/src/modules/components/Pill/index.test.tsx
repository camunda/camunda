/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from 'modules/testing-library';
import Pill from './index';
import {PILL_TYPE} from 'modules/constants';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('Pill', () => {
  const labelString = 'Some Label';

  it('should render label', () => {
    render(<Pill isActive={true}>{labelString}</Pill>, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByText(labelString)).toBeInTheDocument();
  });

  it('should render without icon if no type is passed', () => {
    render(<Pill isActive={true}>{labelString}</Pill>, {
      wrapper: ThemeProvider,
    });
    expect(screen.queryByTestId('target-icon')).not.toBeInTheDocument();
  });

  it('should render with icon', () => {
    render(
      <Pill isActive={true} type={PILL_TYPE.TIMESTAMP}>
        {labelString}
      </Pill>,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByTestId('target-icon')).toBeInTheDocument();
  });

  it('should render count for filter type pills', () => {
    render(
      <Pill isActive={true} type={PILL_TYPE.FILTER} count={10}>
        {labelString}
      </Pill>,
      {
        wrapper: ThemeProvider,
      }
    );
    expect(screen.getByText('10')).toBeInTheDocument();
  });
});
