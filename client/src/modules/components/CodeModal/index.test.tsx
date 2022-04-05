/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import CodeModal from './index';

const validJSON = '{"firstname":"Max","lastname":"Muster","age":31}';
const brokenJSON = '"firstname":"Bro","lastname":"Ken","age":31}';

describe('CodeModal', () => {
  it('should render visible modal in view mode', () => {
    render(
      <CodeModal
        handleModalClose={jest.fn()}
        headline="Some Headline"
        initialValue={validJSON}
        isModalVisible={true}
        mode="view"
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Some Headline')).toBeInTheDocument();
    expect(screen.getByText(/"firstname": "Max"/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /exit modal/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /close modal/i})
    ).toBeInTheDocument();
    expect(screen.getByTestId('editable-content')).toHaveAttribute('disabled');
  });

  it('should render visible modal in edit mode', () => {
    render(
      <CodeModal
        handleModalClose={jest.fn()}
        headline="Some Headline"
        initialValue={validJSON}
        isModalVisible={true}
        mode="edit"
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Some Headline')).toBeInTheDocument();
    expect(screen.getByText(/"firstname": "Max"/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /exit modal/i})
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /close modal/i})
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('editable-content')).not.toHaveAttribute(
      'disabled'
    );
  });

  it('should render non-visible modal', () => {
    render(
      <CodeModal
        handleModalClose={jest.fn()}
        headline="Some Headline"
        initialValue={validJSON}
        isModalVisible={false}
        mode="view"
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.queryByText('Some Headline')).not.toBeInTheDocument();
    expect(screen.queryByText(/"firstname": "Max"/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /exit modal/i})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /close modal/i})
    ).not.toBeInTheDocument();
  });

  it('should close modal', () => {
    const mockHandleCloseModal = jest.fn();
    render(
      <CodeModal
        handleModalClose={mockHandleCloseModal}
        headline="Some Headline"
        initialValue={validJSON}
        isModalVisible={true}
        mode="view"
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    userEvent.click(screen.getByRole('button', {name: /exit modal/i}));
    expect(mockHandleCloseModal).toHaveBeenCalledTimes(1);

    userEvent.click(screen.getByRole('button', {name: /close modal/i}));
    expect(mockHandleCloseModal).toHaveBeenCalledTimes(2);
  });

  it('should still render modal data if initial value is an invalid json', () => {
    const mockHandleCloseModal = jest.fn();
    render(
      <CodeModal
        handleModalClose={mockHandleCloseModal}
        headline="Some Headline"
        initialValue={brokenJSON}
        isModalVisible={true}
        mode="view"
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Some Headline')).toBeInTheDocument();
    expect(screen.getByText(brokenJSON)).toBeInTheDocument();
  });
});
