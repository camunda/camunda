/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ReactComponent as EmptyStateProcessIncidents} from 'modules/components/Icon/empty-state-process-incidents.svg';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {EmptyState} from '.';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('EmptyState', () => {
  it('should render EmptyState with button and link', async () => {
    const buttonSpy = jest.fn();

    const {user} = render(
      <EmptyState
        heading="Nothing to see"
        description="Please move on"
        icon={<EmptyStateProcessIncidents title="Alt Text" />}
        link={{href: '/link-to-home', label: 'Go Home'}}
        button={{label: 'Okay', onClick: buttonSpy}}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.getByText('Nothing to see')).toBeInTheDocument();
    expect(screen.getByText('Please move on')).toBeInTheDocument();
    expect(
      screen.getByText('empty-state-process-incidents.svg')
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Okay'}));
    expect(buttonSpy).toHaveBeenCalledTimes(1);

    expect(screen.getByRole('link', {name: 'Go Home'})).toBeInTheDocument();

    buttonSpy.mockClear();
  });

  it('should render EmptyState without button and link', () => {
    render(
      <EmptyState
        heading="Nothing to see"
        description="Please move on"
        icon={<EmptyStateProcessIncidents title="Alt Text" />}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.getByText('Nothing to see')).toBeInTheDocument();
    expect(screen.getByText('Please move on')).toBeInTheDocument();
    expect(
      screen.getByText('empty-state-process-incidents.svg')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
