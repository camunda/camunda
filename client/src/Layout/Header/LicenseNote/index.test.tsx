/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {LicenseNote} from './index';

const licenseText =
  /Non-Production License. If you would like information on production usage, please refer to our/;

describe('<LicenseNote />', () => {
  it('should show and hide license information', () => {
    render(<LicenseNote />, {wrapper: MockThemeProvider});

    expect(screen.queryByText(licenseText)).not.toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {name: 'Non-Production License'}),
    );
    expect(screen.getByText(licenseText)).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'contact sales'}),
    ).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {name: 'Non-Production License'}),
    );
    expect(screen.queryByText(licenseText)).not.toBeInTheDocument();
  });
});
