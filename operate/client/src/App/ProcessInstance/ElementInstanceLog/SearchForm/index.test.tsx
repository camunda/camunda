/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import {SearchForm} from './index';
import {Paths} from 'modules/Routes';

const PROCESS_INSTANCE_KEY = '1';

const LocationSpy: React.FC<{onLocation: (search: string) => void}> = ({
  onLocation,
}) => {
  const location = useLocation();
  onLocation(location.search);
  return null;
};

const BackButton: React.FC = () => {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(-1)}>
      Go back
    </button>
  );
};

const Wrapper = ({
  children,
  initialSearch = '',
  onLocation,
}: {
  children: React.ReactNode;
  initialSearch?: string;
  onLocation?: (search: string) => void;
}) => (
  <MemoryRouter
    initialEntries={[
      `${Paths.processInstance(PROCESS_INSTANCE_KEY)}${initialSearch}`,
    ]}
  >
    <Routes>
      <Route
        path={Paths.processInstance()}
        element={
          <>
            {children}
            {onLocation && <LocationSpy onLocation={onLocation} />}
          </>
        }
      />
    </Routes>
  </MemoryRouter>
);

describe('<SearchForm />', () => {
  it('sets the URL param after typing and AutoSubmit debounce fires', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    const onLocation = vi.fn();

    const {user} = render(<SearchForm />, {
      wrapper: ({children}) => (
        <Wrapper onLocation={onLocation}>{children}</Wrapper>
      ),
    });

    await user.type(
      screen.getByRole('searchbox', {name: 'Search instance history'}),
      'order',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() => {
      expect(onLocation).toHaveBeenCalledWith(
        expect.stringContaining('elementSearch=order'),
      );
    });

    vi.useRealTimers();
  });

  it('populates the input from the elementSearch URL param on mount', () => {
    render(<SearchForm />, {
      wrapper: ({children}) => (
        <Wrapper initialSearch="?elementSearch=order+task">{children}</Wrapper>
      ),
    });

    expect(
      screen.getByRole('searchbox', {name: 'Search instance history'}),
    ).toHaveValue('order task');
  });

  it('syncs the input when the elementSearch param changes via browser back navigation', async () => {
    const {user} = render(
      <>
        <SearchForm />
        <BackButton />
      </>,
      {
        wrapper: ({children}) => (
          <MemoryRouter
            initialEntries={[
              `${Paths.processInstance(PROCESS_INSTANCE_KEY)}?elementSearch=order`,
              `${Paths.processInstance(PROCESS_INSTANCE_KEY)}?elementSearch=order+task`,
            ]}
            initialIndex={1}
          >
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        ),
      },
    );

    expect(
      screen.getByRole('searchbox', {name: 'Search instance history'}),
    ).toHaveValue('order task');

    await user.click(screen.getByRole('button', {name: 'Go back'}));

    expect(
      screen.getByRole('searchbox', {name: 'Search instance history'}),
    ).toHaveValue('order');
  });

  it('removes the URL param when the input is cleared', async () => {
    const onLocation = vi.fn();

    const {user} = render(<SearchForm />, {
      wrapper: ({children}) => (
        <Wrapper initialSearch="?elementSearch=order" onLocation={onLocation}>
          {children}
        </Wrapper>
      ),
    });

    await user.click(screen.getByRole('button', {name: /clear/i}));

    expect(onLocation).toHaveBeenCalledWith(
      expect.not.stringContaining('elementSearch'),
    );
  });
});
