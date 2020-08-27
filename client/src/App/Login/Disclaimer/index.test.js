/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';

import {Disclaimer} from './index';

const DISCLAIMER_TEXT =
  'This Camunda Operate distribution is available under an evaluation license that is valid for development (non-production) use only. By continuing using this software, you agree to the Terms and Conditions of the Operate Trial Version.';

describe('<Disclaimer />', () => {
  it('should show the disclaimer', () => {
    const {rerender} = render(<Disclaimer />);

    // we need this custom selector because the text contains a link
    expect(
      screen.getByText((content, element) => {
        return content !== '' && element.textContent === DISCLAIMER_TEXT;
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'Terms and Conditions'})
    ).toHaveAttribute(
      'href',
      'https://zeebe.io/legal/operate-evaluation-license'
    );

    rerender(<Disclaimer isEnterprise={false} />);

    expect(
      screen.getByText((content, element) => {
        return content !== '' && element.textContent === DISCLAIMER_TEXT;
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'Terms and Conditions'})
    ).toHaveAttribute(
      'href',
      'https://zeebe.io/legal/operate-evaluation-license'
    );
  });

  it('should not render the disclaimer', () => {
    render(<Disclaimer isEnterprise={true} />);

    expect(
      screen.queryByText((content, element) => {
        return content !== '' && element.textContent === DISCLAIMER_TEXT;
      })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Terms and Conditions'})
    ).not.toBeInTheDocument();
  });
});
