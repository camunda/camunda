/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Collapse} from './index';

describe('<Collapse />', () => {
  it('should be collapsed by default', () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockButtonTitle = 'button-title';

    render(
      <Collapse
        content={mockContent}
        header={mockHeader}
        buttonTitle={mockButtonTitle}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText(mockHeader)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: mockButtonTitle})
    ).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });

  it('should uncollapse and collapse', async () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockButtonTitle = 'button-title';

    const {user} = render(
      <Collapse
        content={mockContent}
        header={mockHeader}
        buttonTitle={mockButtonTitle}
      />,
      {wrapper: ThemeProvider}
    );

    await user.click(screen.getByRole('button', {name: mockButtonTitle}));

    expect(screen.getByText(new RegExp(mockContent))).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: mockButtonTitle}));

    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });
});
