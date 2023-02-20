/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {ProcessOperations} from '.';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('<ProcessOperations />', () => {
  it('should open and close delete modal', async () => {
    const {user} = render(
      <ProcessOperations processName="myProcess" processVersion="2" />,
      {wrapper: Wrapper}
    );

    user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(await screen.findByTestId('modal')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: /delete process definition/i})
    ).toBeInTheDocument();

    user.click(
      screen.getByRole('button', {
        name: 'Cancel',
      })
    );

    await waitForElementToBeRemoved(screen.getByTestId('modal'));
  });

  it('should render confirmation checkbox', async () => {
    const {user} = render(
      <ProcessOperations processName="myProcess" processVersion="2" />,
      {wrapper: Wrapper}
    );

    user.click(
      screen.getByRole('button', {
        name: /^delete process definition "myProcess - version 2"$/i,
      })
    );

    expect(await screen.findByTestId('modal')).toBeInTheDocument();

    expect(
      screen.getByRole('checkbox', {
        name: /Yes, I confirm I want to delete this process definition./i,
      })
    ).toBeInTheDocument();
  });
});
