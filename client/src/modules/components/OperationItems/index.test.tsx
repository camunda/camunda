/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {noop} from 'lodash';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import OperationItems from './index';

describe('OperationItems', () => {
  it('should render with its children', () => {
    render(
      <OperationItems>
        <OperationItems.Item type="RESOLVE_INCIDENT" onClick={noop} />
      </OperationItems>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByRole('listitem')).toBeInTheDocument();
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  describe('Retry Item', () => {
    it('should show the correct icon based on the type', () => {
      render(
        <OperationItems>
          <OperationItems.Item type="RESOLVE_INCIDENT" onClick={noop} />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      expect(screen.getByTestId('retry-operation-icon')).toBeInTheDocument();
    });

    it('should render retry button', () => {
      const BUTTON_TITLE = 'Retry Instance 1';
      render(
        <OperationItems>
          <OperationItems.Item
            type="RESOLVE_INCIDENT"
            onClick={noop}
            title={BUTTON_TITLE}
          />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.getByRole('button', {name: BUTTON_TITLE})
      ).toBeInTheDocument();
    });

    it('should execute the passed method when clicked', () => {
      const BUTTON_TITLE = 'Retry Instance 1';
      const MOCK_ON_CLICK = jest.fn();
      render(
        <OperationItems>
          <OperationItems.Item
            type="RESOLVE_INCIDENT"
            onClick={MOCK_ON_CLICK}
            title={BUTTON_TITLE}
          />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      userEvent.click(screen.getByRole('button', {name: BUTTON_TITLE}));

      expect(MOCK_ON_CLICK).toHaveBeenCalled();
    });
  });

  describe('Cancel Item', () => {
    it('should show the correct icon based on the type', () => {
      render(
        <OperationItems>
          <OperationItems.Item type="CANCEL_PROCESS_INSTANCE" onClick={noop} />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      expect(screen.getByTestId('cancel-operation-icon')).toBeInTheDocument();
    });

    it('should render cancel button', () => {
      const BUTTON_TITLE = 'Cancel Instance 1';
      render(
        <OperationItems>
          <OperationItems.Item
            type="CANCEL_PROCESS_INSTANCE"
            onClick={noop}
            title={BUTTON_TITLE}
          />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.getByRole('button', {name: BUTTON_TITLE})
      ).toBeInTheDocument();
    });

    it('should execute the passed method when clicked', () => {
      const BUTTON_TITLE = 'Cancel Instance 1';
      const MOCK_ON_CLICK = jest.fn();
      render(
        <OperationItems>
          <OperationItems.Item
            type="CANCEL_PROCESS_INSTANCE"
            onClick={MOCK_ON_CLICK}
            title={BUTTON_TITLE}
          />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      userEvent.click(screen.getByRole('button', {name: BUTTON_TITLE}));

      expect(MOCK_ON_CLICK).toHaveBeenCalled();
    });
  });

  describe('Delete Item', () => {
    it('should show the correct icon based on the type', () => {
      render(
        <OperationItems>
          <OperationItems.Item type="DELETE_PROCESS_INSTANCE" onClick={noop} />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      expect(screen.getByTestId('delete-operation-icon')).toBeInTheDocument();
    });

    it('should render delete button', () => {
      const BUTTON_TITLE = 'Delete Instance 1';
      render(
        <OperationItems>
          <OperationItems.Item
            type="DELETE_PROCESS_INSTANCE"
            onClick={noop}
            title={BUTTON_TITLE}
          />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.getByRole('button', {name: BUTTON_TITLE})
      ).toBeInTheDocument();
    });

    it('should execute the passed method when clicked', () => {
      const BUTTON_TITLE = 'Delete Instance 1';
      const MOCK_ON_CLICK = jest.fn();
      render(
        <OperationItems>
          <OperationItems.Item
            type="DELETE_PROCESS_INSTANCE"
            onClick={MOCK_ON_CLICK}
            title={BUTTON_TITLE}
          />
        </OperationItems>,
        {wrapper: ThemeProvider}
      );

      userEvent.click(screen.getByRole('button', {name: BUTTON_TITLE}));

      expect(MOCK_ON_CLICK).toHaveBeenCalled();
    });
  });
});
