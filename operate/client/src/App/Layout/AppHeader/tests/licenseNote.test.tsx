/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AppHeader} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {Wrapper} from './mocks';
import {mockFetchLicense} from 'modules/mocks/api/v2/fetchLicense';
import {licenseTagStore} from 'modules/stores/licenseTag';

jest.unmock('modules/stores/licenseTag');

describe('license note', () => {
  it('should show and hide license information', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'production',
      validLicense: false,
    });

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(licenseTagStore.state.status).toEqual('fetched'),
    );

    expect(
      await screen.findByText(/^Non-production license$/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /learn more/i}));

    expect(
      screen.getByText(
        /Non-production license. For production usage details, visit our/i,
      ),
    ).toBeInTheDocument();
  });

  it('should show license note in self-managed free/trial environment', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'production',
      validLicense: false,
    });

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(licenseTagStore.state.status).toEqual('fetched'),
    );

    expect(
      await screen.findByText(/^Non-production license$/i),
    ).toBeInTheDocument();
  });

  it('should not show license note in SaaS environment', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'saas',
      validLicense: false,
    });

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(licenseTagStore.state.status).toEqual('fetched'),
    );

    expect(
      screen.queryByText(/^Non-production license$/i),
    ).not.toBeInTheDocument();
  });

  it('should show license note in unknown environment', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'unknown',
      validLicense: false,
    });

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(licenseTagStore.state.status).toEqual('fetched'),
    );

    expect(
      await screen.findByText(/^Non-production license$/i),
    ).toBeInTheDocument();
  });

  it('should show license note in self-managed enterprise environment', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'production',
      validLicense: true,
    });

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(licenseTagStore.state.status).toEqual('fetched'),
    );

    expect(
      await screen.findByText(/^production license$/i),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/^Non-production license$/i),
    ).not.toBeInTheDocument();
  });

  it('should not show license note on fetch error', async () => {
    mockFetchLicense().withServerError();

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(licenseTagStore.state.status).toEqual('error'));

    expect(
      screen.queryByText(/^Non-production license$/i),
    ).not.toBeInTheDocument();

    expect(screen.queryByText(/^production license$/i)).not.toBeInTheDocument();
  });
});
