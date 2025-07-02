/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {TextViewer} from '.';

// Mock Monaco Editor since it requires complex setup for testing
vi.mock('@monaco-editor/react', () => ({
  default: ({value, language, ...props}: any) => (
    <div 
      data-testid="monaco-editor"
      data-language={language}
      data-value={value}
      {...props}
    >
      Monaco Editor Mock: {value}
    </div>
  ),
}));

describe('<TextViewer />', () => {
  it('should render text content', () => {
    const textValue = 'Hello, this is a test file\nwith multiple lines.';
    
    render(<TextViewer value={textValue} />);
    
    const editor = screen.getByTestId('monaco-editor');
    expect(editor).toBeInTheDocument();
    expect(editor).toHaveAttribute('data-language', 'plaintext');
    expect(editor).toHaveAttribute('data-value', textValue);
  });

  it('should apply custom height', () => {
    const textValue = 'Simple text';
    const customHeight = '400px';
    
    render(<TextViewer value={textValue} height={customHeight} />);
    
    const editor = screen.getByTestId('monaco-editor');
    expect(editor).toBeInTheDocument();
    expect(editor).toHaveAttribute('height', customHeight);
  });

  it('should have proper test id', () => {
    const textValue = 'Test content';
    
    render(<TextViewer value={textValue} data-testid="text-test" />);
    
    expect(screen.getByTestId('text-test')).toBeInTheDocument();
  });

  it('should handle empty text', () => {
    render(<TextViewer value="" />);
    
    const editor = screen.getByTestId('monaco-editor');
    expect(editor).toBeInTheDocument();
    expect(editor).toHaveAttribute('data-value', '');
  });
});