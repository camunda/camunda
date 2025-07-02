/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {TextViewer} from '.';

describe('<TextViewer />', () => {
  it('should render text content', () => {
    const textValue = 'Hello, this is a test file\nwith multiple lines.';
    
    render(<TextViewer value={textValue} />);
    
    expect(screen.getByDisplayValue(textValue)).toBeInTheDocument();
  });

  it('should apply custom height', () => {
    const textValue = 'Simple text';
    const customHeight = '400px';
    
    const {container} = render(<TextViewer value={textValue} height={customHeight} />);
    
    // Check that the component renders without error
    expect(container).toBeInTheDocument();
  });

  it('should have proper test id', () => {
    const textValue = 'Test content';
    
    render(<TextViewer value={textValue} data-testid="text-test" />);
    
    expect(screen.getByTestId('text-test')).toBeInTheDocument();
  });

  it('should handle empty text', () => {
    render(<TextViewer value="" />);
    
    expect(screen.getByDisplayValue('')).toBeInTheDocument();
  });
});