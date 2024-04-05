/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
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
    ).toHaveAttribute(
      'href',
      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-self-managed/',
    );
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
    ).toHaveAttribute(
      'href',
      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-self-managed/',
    );
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
});
