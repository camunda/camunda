/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import Checkbox from './index';

describe('<Checkbox />', () => {
  it('should toggle checkbox', () => {
    const label = 'A checkbox label';
    const MOCK_ON_CHANGE = jest.fn();
    const {rerender} = render(
      <Checkbox onChange={MOCK_ON_CHANGE} isChecked={false} label={label} />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByRole('checkbox', {name: label})).not.toBeChecked();

    userEvent.click(screen.getByRole('checkbox', {name: label}));

    expect(MOCK_ON_CHANGE).toHaveBeenCalledWith(expect.anything(), true);

    rerender(
      <Checkbox onChange={MOCK_ON_CHANGE} isChecked={true} label={label} />
    );

    userEvent.click(screen.getByRole('checkbox', {name: label}));

    expect(MOCK_ON_CHANGE).toHaveBeenNthCalledWith(2, expect.anything(), false);
  });
});
