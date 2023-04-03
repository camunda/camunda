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

describe('FirstTimeModal', () => {
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  it('should render an alpha notice modal', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      organizationId: '1-1-1',
    };
    render(<FirstTimeModal />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('alpha-warning-modal-image')).toBeInTheDocument();
    expect(
      screen.getByLabelText('This is an alpha feature'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('dialog', {name: 'Start your process on demand'}),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Start processes on demand directly from your tasklist.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'You can execute all of your processes at any time as long as you are eligible to work on tasks inside your project.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'By starting processes on demand you are able to trigger tasks and directly start assigning these.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('alert', {name: 'Alpha feature'}),
    ).toBeInTheDocument();
    expect(
      screen.getByText('this feature is only available for alpha releases.'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Read consent'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Continue'})).toBeInTheDocument();
  });

  it('should render process modal without alpha labels for SM', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      organizationId: null,
    };
    render(<FirstTimeModal />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('alpha-warning-modal-image')).toBeInTheDocument();
    expect(screen.queryByLabelText('This is an alpha feature')).toBeNull();
  });

  it('should open the alpha consent', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      organizationId: '1-1-1',
    };
    Object.defineProperty(window, 'open', {
      writable: true,
      value: jest.fn(),
    });

    const {user} = render(<FirstTimeModal />, {
      wrapper: Wrapper,
    });

    await user.click(screen.getByRole('button', {name: 'Read consent'}));

    expect(window.open).toHaveBeenCalledWith(
      'https://docs.camunda.io/docs/reference/early-access/#alpha',
      '_blank',
      'noopener noreferrer',
    );

    Object.defineProperty(window, 'open', {
      writable: true,
      value: undefined,
    });
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
