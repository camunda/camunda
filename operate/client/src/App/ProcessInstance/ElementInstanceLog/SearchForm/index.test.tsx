/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter, Route, Routes, useLocation} from 'react-router-dom';
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
  it('populates the input from the elementSearch URL param on mount', () => {
    render(<SearchForm />, {
      wrapper: ({children}) => (
        <Wrapper initialSearch="?elementSearch=order+task">{children}</Wrapper>
      ),
    });

    expect(screen.getByLabelText('Filter element instances')).toHaveValue(
      'order task',
    );
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
