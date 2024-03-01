/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {JSONEditorModal} from './index';
import {render, screen} from 'modules/testing-library';

describe('<JSONEditorModal />', () => {
  it('should not render the modal', () => {
    render(<JSONEditorModal isVisible={false} value="" />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should render modal', () => {
    const mockValue = '"fooo"';
    const mockTitle = 'i am a title';

    const {rerender} = render(
      <JSONEditorModal isVisible value={mockValue} title={mockTitle} />,
    );

    expect(
      screen.getByRole('heading', {
        name: mockTitle,
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockValue)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();

    rerender(
      <JSONEditorModal
        isVisible
        readOnly
        value={mockValue}
        title={mockTitle}
      />,
    );

    expect(
      screen.getByRole('heading', {
        name: mockTitle,
      }),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockValue)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /apply/i}),
    ).not.toBeInTheDocument();
  });

  it('should handle modal close', async () => {
    const mockOnClose = jest.fn();

    const {user, rerender} = render(
      <JSONEditorModal isVisible value="" onClose={mockOnClose} />,
    );

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    rerender(
      <JSONEditorModal isVisible readOnly value="" onClose={mockOnClose} />,
    );

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it('should handle modal apply', async () => {
    const mockOnApply = jest.fn();
    const mockValue = '"i am a value"';

    const {user} = render(
      <JSONEditorModal isVisible value={mockValue} onApply={mockOnApply} />,
    );

    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnApply).toHaveBeenNthCalledWith(1, mockValue);
  });

  it('should handle value edit', async () => {
    const mockOnApply = jest.fn();
    const mockInitialValue = '"i am a value"';
    const mockUpdatedValue = '"i am an updated value"';

    const {user} = render(
      <JSONEditorModal
        isVisible
        value={mockInitialValue}
        onApply={mockOnApply}
      />,
    );

    const editor = screen.getByDisplayValue(mockInitialValue);

    await user.clear(editor);
    await user.type(editor, mockUpdatedValue);
    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnApply).toHaveBeenNthCalledWith(1, mockUpdatedValue);
  });
});
