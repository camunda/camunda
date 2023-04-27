/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';
import {FirstTimeModal} from '.';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <MemoryRouter initialEntries={['/']}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </MemoryRouter>
  );
};

describe('<FirstTimeModal />', () => {
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

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
