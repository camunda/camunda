/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {FirstTimeModal} from '.';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return <MemoryRouter initialEntries={['/']}>{children}</MemoryRouter>;
};

describe('<FirstTimeModal />', () => {
  it('should save the consent', async () => {
    const {user} = render(<FirstTimeModal />, {
      wrapper: Wrapper,
    });

    expect(
      window.localStorage.getItem('hasConsentedToStartProcess'),
    ).toBeNull();

    await user.click(screen.getByRole('button', {name: 'Continue'}));

    expect(window.localStorage.getItem('hasConsentedToStartProcess')).toEqual(
      'true',
    );
  });

  it('should no render notice modal if consent is saved', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');

    render(<FirstTimeModal />, {
      wrapper: Wrapper,
    });

    expect(screen.queryByTestId('alpha-warning-modal-image')).toBeNull();
  });
});
