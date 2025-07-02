/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {JSONViewer} from '.';

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

describe('<JSONViewer />', () => {
  it('should render JSON content with monaco editor', () => {
    const jsonValue = '{"name": "test", "value": 123}';
    
    render(<JSONViewer value={jsonValue} />);
    
    const editor = screen.getByTestId('monaco-editor');
    expect(editor).toBeInTheDocument();
    expect(editor).toHaveAttribute('data-language', 'json');
    expect(editor).toHaveAttribute('data-value', JSON.stringify(JSON.parse(jsonValue), null, 2));
  });

  it('should handle invalid JSON gracefully', () => {
    const invalidJson = '{"invalid": json}';
    
    render(<JSONViewer value={invalidJson} />);
    
    const editor = screen.getByTestId('monaco-editor');
    expect(editor).toBeInTheDocument();
    expect(editor).toHaveAttribute('data-value', invalidJson);
  });

  it('should apply custom height', () => {
    const jsonValue = '{"test": true}';
    const customHeight = '500px';
    
    render(<JSONViewer value={jsonValue} height={customHeight} />);
    
    const editor = screen.getByTestId('monaco-editor');
    expect(editor).toBeInTheDocument();
    expect(editor).toHaveAttribute('height', customHeight);
  });

  it('should have proper test id', () => {
    const jsonValue = '{"test": true}';
    
    render(<JSONViewer value={jsonValue} data-testid="json-test" />);
    
    expect(screen.getByTestId('json-test')).toBeInTheDocument();
  });
});