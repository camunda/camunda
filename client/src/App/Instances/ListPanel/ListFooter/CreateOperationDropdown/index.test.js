/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  fireEvent,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {InstanceSelectionProvider} from 'modules/contexts/InstanceSelectionContext';
import {mockUseOperationApply, instanceSelectionContext} from './index.setup';
import CreateOperationDropdown from './index';
import PropTypes from 'prop-types';

jest.mock('./useOperationApply', () => () => mockUseOperationApply);

const Wrapper = ({children}) => {
  return (
    <InstanceSelectionProvider value={instanceSelectionContext}>
      <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
    </InstanceSelectionProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('CreateOperationDropdown', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it('should not initially display confirmation model', () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    expect(screen.queryByText('Apply Operation')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Retry'})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Cancel'})
    ).not.toBeInTheDocument();
  });

  it('should show dropdown menu on click', () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    fireEvent.click(screen.getByRole('button', {name: /^MyLabel/}));

    expect(screen.getByRole('button', {name: 'Retry'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
  });

  it('should show modal on retry click', () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    fireEvent.click(screen.getByRole('button', {name: /^MyLabel/}));

    fireEvent.click(screen.getByRole('button', {name: 'Retry'}));

    expect(screen.getByText('Apply Operation')).toBeInTheDocument();
    expect(screen.getByText('About to retry 2 Instances.')).toBeInTheDocument();
    expect(screen.getByText('Click "Apply" to proceed.')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: 'Apply'})
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: 'Cancel'})
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {
        name: 'Close Modal',
      })
    ).toBeInTheDocument();
  });

  it('should show modal on cancel click', () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    fireEvent.click(screen.getByRole('button', {name: /^MyLabel/}));

    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.getByText('Apply Operation')).toBeInTheDocument();
    expect(
      screen.getByText('About to cancel 2 Instances.')
    ).toBeInTheDocument();
    expect(screen.getByText('Click "Apply" to proceed.')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: 'Apply'})
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: 'Cancel'})
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {
        name: 'Close Modal',
      })
    ).toBeInTheDocument();
  });

  it('should close modal on apply batch operations', async () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    fireEvent.click(screen.getByRole('button', {name: /^MyLabel/}));

    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    fireEvent.click(
      within(screen.getByTestId('modal')).getByRole('button', {name: 'Apply'})
    );

    await waitForElementToBeRemoved(screen.getByText('Apply Operation'));
  });

  it('should close modal on cancel batch operations', async () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    fireEvent.click(screen.getByRole('button', {name: /^MyLabel/}));

    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    fireEvent.click(
      within(screen.getByTestId('modal')).getByRole('button', {name: 'Cancel'})
    );

    await waitForElementToBeRemoved(screen.getByText('Apply Operation'));
  });

  it('should close modal on close button click', async () => {
    render(<CreateOperationDropdown label="MyLabel" selectedCount={2} />, {
      wrapper: Wrapper,
    });

    fireEvent.click(screen.getByRole('button', {name: /^MyLabel/}));

    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    fireEvent.click(
      within(screen.getByTestId('modal')).getByRole('button', {
        name: 'Close Modal',
      })
    );

    await waitForElementToBeRemoved(screen.getByText('Apply Operation'));
  });
});
