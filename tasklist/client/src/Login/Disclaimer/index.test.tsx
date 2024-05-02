/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Disclaimer} from './index';

const DISCLAIMER_TEXT =
  'Non-Production License. If you would like information on production usage, please refer to our terms & conditions page or contact sales.';

describe('<Disclaimer />', () => {
  beforeEach(() => {
    Object.defineProperties(window.clientConfig, {
      isEnterprise: {
        configurable: true,
        writable: true,
        value: null,
      },
    });
  });
  afterEach(() => {
    delete window.clientConfig!.isEnterprise;
  });

  it('should show the disclaimer', () => {
    const {rerender} = render(<Disclaimer />, {wrapper: MockThemeProvider});

    // we need this custom selector because the text contains a link
    expect(
      screen.getByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'contact sales'})).toHaveAttribute(
      'href',
      'https://camunda.com/contact/',
    );

    window.clientConfig!.isEnterprise = false;

    rerender(<Disclaimer />);

    expect(
      screen.getByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'contact sales'})).toHaveAttribute(
      'href',
      'https://camunda.com/contact/',
    );
  });

  it('should not render the disclaimer', () => {
    window.clientConfig!.isEnterprise = true;
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

  it('should open terms & conditions modal', async () => {
    const {user} = render(<Disclaimer />, {wrapper: MockThemeProvider});

    await user.click(
      screen.getByRole('link', {
        name: 'terms & conditions page',
      }),
    );

    expect(
      within(
        screen.getByRole('dialog', {
          name: /terms & conditions/i,
        }),
      ).getByRole('button', {
        name: /close/i,
      }),
    ).toHaveFocus();
  });
});
