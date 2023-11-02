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

    await user.click(screen.getByRole('button', {name: 'Back'}));

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();
  });
});
