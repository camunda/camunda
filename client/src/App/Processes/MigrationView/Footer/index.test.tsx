/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {Footer} from '.';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useEffect} from 'react';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => processInstanceMigrationStore.reset);
  return <>{children}</>;
};

describe('Footer', () => {
  beforeEach(() => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');
  });

  it('should render correct buttons in each step', async () => {
    const {user} = render(<Footer />, {wrapper: Wrapper});

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Next'}));

    expect(
      screen.queryByRole('button', {name: 'Next'}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Back'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Confirm'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Back'}));

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();
  });

  it('should display confirmation modal on exit migration click', async () => {
    const {user} = render(<Footer />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: 'Exit migration'}));

    expect(
      screen.getByText(
        /You are about to leave ongoing migration, all planned mapping\/s will be discarded./,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Click “Exit” to proceed./)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(
      screen.queryByText(
        /You are about to leave ongoing migration, all planned mapping\/s will be discarded./,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Click “Exit” to proceed./),
    ).not.toBeInTheDocument();

    expect(processInstanceMigrationStore.isEnabled).toBe(true);

    await user.click(screen.getByRole('button', {name: 'Exit migration'}));

    await user.click(screen.getByRole('button', {name: 'danger Exit'}));
    expect(processInstanceMigrationStore.isEnabled).toBe(false);
  });
});
