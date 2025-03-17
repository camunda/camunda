/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {Disclaimer} from './index';
import {vi} from 'vitest';
import {getClientConfig} from 'common/config/getClientConfig';

vi.mock('common/config/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('common/config/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('common/config/getClientConfig')
>('common/config/getClientConfig');
const mockGetClientConfig = vi.mocked(getClientConfig);

const DISCLAIMER_TEXT =
  'Non-Production License. If you would like information on production usage, please refer to our terms & conditions page or contact sales.';

describe('<Disclaimer />', () => {
  it('should show the disclaimer', () => {
    mockGetClientConfig.mockReturnValue(actualGetClientConfig());

    const {rerender} = render(<Disclaimer />);

    // we need this custom selector because the text contains a link
    expect(
      screen.getByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'}),
    ).toHaveAttribute(
      'href',
      'https://legal.camunda.com/#self-managed-non-production-terms',
    );
    expect(screen.getByRole('link', {name: 'contact sales'})).toHaveAttribute(
      'href',
      'https://camunda.com/contact/',
    );

    rerender(<Disclaimer />);

    expect(
      screen.getByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'}),
    ).toHaveAttribute(
      'href',
      'https://legal.camunda.com/#self-managed-non-production-terms',
    );
    expect(screen.getByRole('link', {name: 'contact sales'})).toHaveAttribute(
      'href',
      'https://camunda.com/contact/',
    );
  });

  it('should not render the disclaimer', () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      isEnterprise: true,
    });

    render(<Disclaimer />);

    expect(
      screen.queryByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'terms & conditions page'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'contact sales'}),
    ).not.toBeInTheDocument();
  });
});
