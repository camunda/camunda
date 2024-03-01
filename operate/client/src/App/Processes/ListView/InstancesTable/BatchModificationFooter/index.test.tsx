/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {render, screen} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {BatchModificationFooter} from '.';

const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
  ({children}) => {
    useEffect(() => {
      processInstancesStore.setProcessInstances({
        filteredProcessInstancesCount: 10,
        processInstances: [],
      });

      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
      };
    });

    return (
      <>
        {children}
        <button
          onClick={processInstancesSelectionStore.selectAllProcessInstances}
        >
          Toggle select all instances
        </button>
        <button
          onClick={() =>
            processInstancesSelectionStore.selectProcessInstance('123')
          }
        >
          select single instance
        </button>
      </>
    );
  },
);

describe('BatchModificationFooter', () => {
  it('should disable apply button when no instances are selected', async () => {
    render(<BatchModificationFooter />, {wrapper: Wrapper});

    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeDisabled();
  });

  it('should enable apply button when all instances are selected', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    // select all instances
    await user.click(
      screen.getByRole('button', {name: /toggle select all instances/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();

    // deselect all instances
    await user.click(
      screen.getByRole('button', {name: /toggle select all instances/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeDisabled();
  });

  it('should enable apply button when one instance is selected', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select single instance/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();
  });

  it('should enable apply button when one instance is excluded', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /toggle select all instances/i}),
    );
    await user.click(
      screen.getByRole('button', {name: /select single instance/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();
  });
});
