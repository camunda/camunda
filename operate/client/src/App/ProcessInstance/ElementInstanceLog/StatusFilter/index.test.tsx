/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {MemoryRouter, Route, Routes, useLocation} from 'react-router-dom';
import {StatusFilter} from './index';
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

describe('<StatusFilter />', () => {
  it('renders All, Active and Incidents without counts', () => {
    render(<StatusFilter />, {wrapper: Wrapper});

    expect(screen.getByRole('button', {name: 'All'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Active'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Incidents'})).toBeInTheDocument();
  });

  it('marks All as pressed by default', () => {
    render(<StatusFilter />, {wrapper: Wrapper});

    expect(screen.getByRole('button', {name: 'All'})).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('reflects the elementStatus URL param on mount', () => {
    render(<StatusFilter />, {
      wrapper: ({children}) => (
        <Wrapper initialSearch="?elementStatus=incidents">{children}</Wrapper>
      ),
    });

    expect(screen.getByRole('button', {name: 'Incidents'})).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', {name: 'All'})).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('sets elementStatus=active in the URL when Active is clicked', async () => {
    const onLocation = vi.fn();

    const {user} = render(<StatusFilter />, {
      wrapper: ({children}) => (
        <Wrapper onLocation={onLocation}>{children}</Wrapper>
      ),
    });

    await user.click(screen.getByRole('button', {name: 'Active'}));

    await waitFor(() => {
      expect(onLocation).toHaveBeenCalledWith(
        expect.stringContaining('elementStatus=active'),
      );
    });
  });

  it('removes elementStatus from the URL when All is clicked again', async () => {
    const onLocation = vi.fn();

    const {user} = render(<StatusFilter />, {
      wrapper: ({children}) => (
        <Wrapper initialSearch="?elementStatus=active" onLocation={onLocation}>
          {children}
        </Wrapper>
      ),
    });

    await user.click(screen.getByRole('button', {name: 'All'}));

    await waitFor(() => {
      expect(onLocation).toHaveBeenCalledWith(
        expect.not.stringContaining('elementStatus'),
      );
    });
  });
});
