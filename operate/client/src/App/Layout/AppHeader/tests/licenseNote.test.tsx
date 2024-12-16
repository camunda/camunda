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
  it('should show non production tag if license is invalid', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'production',
      validLicense: false,
      isCommercial: false,
      expiresAt: new Date().toISOString(),
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
      isCommercial: false,
      expiresAt: new Date().toISOString(),
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

    expect(
      screen.queryByText(/^Non-commercial license - expired$/i),
    ).not.toBeInTheDocument();
  });

  it('should show license note in unknown environment', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'unknown',
      validLicense: false,
      isCommercial: false,
      expiresAt: new Date().toISOString(),
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

    expect(
      await screen.findByText(/^Non-commercial license - expired$/i),
    ).toBeInTheDocument();
  });

  it('should show license note in self-managed enterprise environment', async () => {
    mockFetchLicense().withSuccess({
      licenseType: 'production',
      validLicense: true,
      isCommercial: false,
      expiresAt: new Date().toISOString(),
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

it('should show non-commercial license note in self-managed enterprise environment', async () => {
  mockFetchLicense().withSuccess({
    licenseType: 'production',
    validLicense: true,
    isCommercial: false,
    expiresAt: new Date().toISOString(),
  });

  render(<AppHeader />, {
    wrapper: Wrapper,
  });

  await waitFor(() => expect(licenseTagStore.state.status).toEqual('fetched'));

  expect(
    await screen.findByText(/^Non-commercial license - expired$/i),
  ).toBeInTheDocument();
});

it('should hide commercial license note in self-managed if license is commercial', async () => {
  mockFetchLicense().withSuccess({
    licenseType: 'production',
    validLicense: true,
    isCommercial: true,
    expiresAt: new Date().toISOString(),
  });

  render(<AppHeader />, {
    wrapper: Wrapper,
  });

  await waitFor(() => expect(licenseTagStore.state.status).toEqual('fetched'));

  expect(
    screen.queryByText(/^Non-commercial license - expired$/i),
  ).not.toBeInTheDocument();
});

it('should show non-commercial license expiry date', async () => {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + 1); // Adds one day to the current date in UTC

  mockFetchLicense().withSuccess({
    licenseType: 'production',
    validLicense: true,
    isCommercial: false,
    expiresAt: date.toISOString(),
  });

  render(<AppHeader />, {
    wrapper: Wrapper,
  });

  await waitFor(() => expect(licenseTagStore.state.status).toEqual('fetched'));

  expect(
    await screen.findByText(/^Non-commercial license - 0 day left$/i),
  ).toBeInTheDocument();
});
