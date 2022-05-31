/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Collapse} from './index';
import {Link, MemoryRouter} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/']}>
        {children}
        <LocationLog />
        <Link to="/">go to initial</Link>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('<Collapse />', () => {
  it('should be collapsed by default', () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockTitle = 'mock-title';

    render(
      <Collapse content={mockContent} header={mockHeader} title={mockTitle} />,
      {wrapper: Wrapper}
    );

    expect(screen.getByText(mockHeader)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: mockTitle})).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });

  it('should uncollapse and collapse', async () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockTitle = 'mock-title';

    const {user} = render(
      <Collapse content={mockContent} header={mockHeader} title={mockTitle} />,
      {wrapper: Wrapper}
    );

    await user.click(screen.getByRole('button', {name: mockTitle}));

    expect(screen.getByText(new RegExp(mockContent))).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: mockTitle}));

    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });

  it('should go back to default state on navigation', async () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockTitle = 'mock-title';

    const {user} = render(
      <Collapse content={mockContent} header={mockHeader} title={mockTitle} />,
      {wrapper: Wrapper}
    );

    await user.click(screen.getByRole('button', {name: mockTitle}));

    expect(screen.getByText(new RegExp(mockContent))).toBeInTheDocument();

    await user.click(screen.getByText(/go to initial/));

    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });
});
