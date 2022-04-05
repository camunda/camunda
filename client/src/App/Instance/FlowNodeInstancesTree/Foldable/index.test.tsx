/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import noop from 'lodash/noop';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {Foldable} from './index';

describe('<Foldable />', () => {
  it('should show the unfoldable content', () => {
    const mockContent = 'mock-content';

    render(
      <Foldable isFoldable={false} isFolded={false}>
        <Foldable.Summary
          onSelection={noop}
          isSelected={false}
          isLastChild={false}
          nodeName="node-name"
        >
          {mockContent}
        </Foldable.Summary>
      </Foldable>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText(mockContent)).toBeInTheDocument();
  });

  it('should handle content click', () => {
    const mockContent = 'mock-content';
    const mockOnSelection = jest.fn();

    render(
      <Foldable isFoldable={false} isFolded={false}>
        <Foldable.Summary
          onSelection={mockOnSelection}
          isSelected={false}
          isLastChild={false}
          nodeName="node-name"
        >
          {mockContent}
        </Foldable.Summary>
      </Foldable>,
      {wrapper: ThemeProvider}
    );

    userEvent.click(screen.getByText(mockContent));

    expect(mockOnSelection).toHaveBeenCalled();
  });

  it('should show details', () => {
    const mockContent = 'mock-content';
    const mockDetails = 'mock-details';
    const mockNodeName = 'node-name';

    render(
      <Foldable isFoldable={true} isFolded={false}>
        <Foldable.Summary
          onSelection={noop}
          isSelected={false}
          isLastChild={false}
          nodeName={mockNodeName}
        >
          {mockContent}
        </Foldable.Summary>
        <Foldable.Details>{mockDetails}</Foldable.Details>
      </Foldable>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText(mockContent)).toBeInTheDocument();
    expect(screen.getByText(mockDetails)).toBeInTheDocument();
  });

  it('should handle unfolded details', () => {
    const mockContent = 'mock-content';
    const mockDetails = 'mock-details';

    render(
      <Foldable isFoldable={true} isFolded={false}>
        <Foldable.Summary
          onSelection={noop}
          isSelected={false}
          isLastChild={false}
          nodeName="node-name"
        >
          {mockContent}
        </Foldable.Summary>
        <Foldable.Details>{mockDetails}</Foldable.Details>
      </Foldable>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText(mockContent)).toBeInTheDocument();
    expect(screen.getByText(mockDetails)).toBeInTheDocument();
  });
});
