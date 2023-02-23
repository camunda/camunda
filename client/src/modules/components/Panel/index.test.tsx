/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';

import {Panel} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';

describe('<Panel />', () => {
  it('should show the content and the title', () => {
    const mockTitle = 'Mock title';
    const mockContent = 'content';

    render(<Panel title={mockTitle}>{mockContent}</Panel>, {
      wrapper: MockThemeProvider,
    });

    expect(screen.getByText(mockTitle)).toBeInTheDocument();
    expect(screen.getByText(mockContent)).toBeInTheDocument();
  });

  it('should show and hide the footer', () => {
    const mockFooter = 'copyright notice';

    const {rerender} = render(
      <Panel title="title" footer={mockFooter}>
        content
      </Panel>,
      {
        wrapper: MockThemeProvider,
      },
    );

    expect(screen.getByText(mockFooter)).toBeInTheDocument();

    rerender(<Panel title="title">content</Panel>);

    expect(screen.queryByText(mockFooter)).not.toBeInTheDocument();
  });
});
