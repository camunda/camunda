/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from '@testing-library/react';
import {Frame} from '.';

describe('Frame', () => {
  it('should render frame', () => {
    render(
      <Frame frame={{headerTitle: 'Frame Title', isVisible: true}}>
        <div>Content</div>
      </Frame>,
    );

    expect(screen.getByText('Content')).toBeInTheDocument();
    expect(screen.getByText('Frame Title')).toBeInTheDocument();
    expect(screen.getByTestId('frame-container')).toBeInTheDocument();
    expect(screen.getByTestId('frame-container')).toHaveStyleRule(
      'border',
      expect.any(String),
    );
  });

  it('should not render frame border', () => {
    render(
      <Frame frame={{headerTitle: 'Frame Title', isVisible: false}}>
        <div>Content</div>
      </Frame>,
    );

    expect(screen.getByText('Content')).toBeInTheDocument();
    expect(screen.queryByText('Frame Title')).not.toBeInTheDocument();
    expect(screen.getByTestId('frame-container')).toBeInTheDocument();
    expect(screen.getByTestId('frame-container')).not.toHaveStyleRule('border');
  });

  it('should not render frame', () => {
    render(
      <Frame>
        <div>Content</div>
      </Frame>,
    );

    expect(screen.getByText('Content')).toBeInTheDocument();
    expect(screen.queryByText('Frame Title')).not.toBeInTheDocument();
    expect(screen.queryByTestId('frame-container')).not.toBeInTheDocument();
  });
});
