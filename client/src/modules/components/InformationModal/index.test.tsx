/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {InformationModal} from './index';

const mockProps = {
  onClose: jest.fn(),
  footer: <div>footer content</div>,
  body: <div>body content</div>,
  title: 'modal title',
  isVisible: true,
};

describe('InformationModal', () => {
  it('should render', () => {
    render(<InformationModal {...mockProps} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByText('body content')).toBeInTheDocument();
    expect(screen.getByText('footer content')).toBeInTheDocument();
    expect(screen.getByTestId('cross-button')).toBeInTheDocument();
  });

  it('should call close modal function on close button click', async () => {
    const {user} = render(<InformationModal {...mockProps} />, {
      wrapper: ThemeProvider,
    });
    await user.click(screen.getByTestId('cross-button'));
    expect(mockProps.onClose).toHaveBeenCalledTimes(1);
  });
});
