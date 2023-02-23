/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, fireEvent} from 'modules/testing-library';

import {CollapsablePanel} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';

describe('<CollapsablePanel />', () => {
  it('should render collapsable panel', () => {
    const mockTitle = 'Mock title';
    const mockContent = 'content';

    render(
      <CollapsablePanel title={mockTitle}>{mockContent}</CollapsablePanel>,
      {
        wrapper: MockThemeProvider,
      },
    );

    expect(screen.getByText(mockTitle)).toBeInTheDocument();
    expect(screen.getByText(mockContent)).toBeInTheDocument();
    expect(screen.getByTestId('expanded-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('collapsed-panel')).not.toBeInTheDocument();
  });

  it('should toggle between expanded and collapsed panel', () => {
    const mockTitle = 'Mock title';
    const mockContent = 'content';

    render(
      <CollapsablePanel title={mockTitle}>{mockContent}</CollapsablePanel>,
      {
        wrapper: MockThemeProvider,
      },
    );

    fireEvent.click(screen.getByTestId('collapse-button'));

    expect(screen.getByTestId('collapsed-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('expanded-panel')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('collapsed-panel'));

    expect(screen.getByTestId('expanded-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('collapsed-panel')).not.toBeInTheDocument();
  });
});
