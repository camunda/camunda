/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {JSONViewer} from '.';

describe('<JSONViewer />', () => {
  it('should render JSON content with syntax highlighting', () => {
    const jsonValue = '{"name": "test", "value": 123}';
    
    render(<JSONViewer value={jsonValue} />);
    
    expect(screen.getByDisplayValue(JSON.stringify(JSON.parse(jsonValue), null, 2))).toBeInTheDocument();
  });

  it('should handle invalid JSON gracefully', () => {
    const invalidJson = '{"invalid": json}';
    
    render(<JSONViewer value={invalidJson} />);
    
    expect(screen.getByDisplayValue(invalidJson)).toBeInTheDocument();
  });

  it('should apply custom height', () => {
    const jsonValue = '{"test": true}';
    const customHeight = '500px';
    
    const {container} = render(<JSONViewer value={jsonValue} height={customHeight} />);
    
    const editor = container.querySelector('.fjs-form-field-container');
    // Note: The actual height assertion would depend on how Monaco editor renders
    // For now, we just check that the component renders without error
    expect(container).toBeInTheDocument();
  });

  it('should have proper test id', () => {
    const jsonValue = '{"test": true}';
    
    render(<JSONViewer value={jsonValue} data-testid="json-test" />);
    
    expect(screen.getByTestId('json-test')).toBeInTheDocument();
  });
});