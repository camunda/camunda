/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DiffEditorModal} from './index';
import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

const mockEditorModalProps = {
  title: 'i am a title',
  onClose: jest.fn(),
};

describe('<DiffEditorModal />', () => {
  it('should not render the modal', () => {
    render(
      <DiffEditorModal
        {...mockEditorModalProps}
        isVisible={false}
        originalValue=""
        modifiedValue="2"
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should render modal', () => {
    const mockOriginalValue = '"fooo"';
    const mockModifiedValue = '"fooo2"';
    const mockTitle = 'i am a title';

    render(
      <DiffEditorModal
        {...mockEditorModalProps}
        isVisible
        originalValue={mockOriginalValue}
        modifiedValue={mockModifiedValue}
        title={mockTitle}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();

    expect(
      screen.getByRole('heading', {
        name: mockTitle,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /exit modal/i})
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockOriginalValue)).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockModifiedValue)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();
  });

  it('should handle modal close', async () => {
    const mockOnClose = jest.fn();

    const {user} = render(
      <DiffEditorModal
        {...mockEditorModalProps}
        isVisible
        originalValue=""
        modifiedValue="123"
        onClose={mockOnClose}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    await user.click(screen.getByRole('button', {name: /exit modal/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });
});
