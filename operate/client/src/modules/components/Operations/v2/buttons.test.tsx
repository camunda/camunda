/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {storeStateLocally} from 'modules/utils/localStorage';
import {Operations} from '.';
import {FAILED_OPERATION, INSTANCE, getWrapper} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance} from 'modules/testUtils';

describe('Operations - Buttons', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(
      createInstance({
        operations: [FAILED_OPERATION],
      }),
    );
  });

  it('should render retry, cancel and modify buttons if instance is running and has an incident', () => {
    render(
      <Operations
        instance={{...INSTANCE, hasIncident: true}}
        isInstanceModificationVisible
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTitle('Retry Instance instance_1')).toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Modify Instance instance_1')).toBeInTheDocument();
  });

  it('should render cancel and modify buttons if instance is running and does not have an incident', () => {
    render(
      <Operations
        instance={{...INSTANCE, state: 'ACTIVE'}}
        isInstanceModificationVisible
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.queryByTitle('Retry Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Modify Instance instance_1')).toBeInTheDocument();
  });

  it('should render delete button if instance is completed', () => {
    render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'COMPLETED',
        }}
        isInstanceModificationVisible
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.queryByTitle('Retry Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Cancel Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Delete Instance instance_1')).toBeInTheDocument();

    expect(
      screen.queryByTitle('Modify Instance instance_1'),
    ).not.toBeInTheDocument();
  });

  it('should render delete button if instance is canceled', () => {
    render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'TERMINATED',
        }}
        isInstanceModificationVisible
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.queryByTitle('Retry Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Cancel Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Delete Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Modify Instance instance_1'),
    ).not.toBeInTheDocument();
  });

  it('should hide operation buttons in process instance modification mode', async () => {
    const {user} = render(
      <Operations
        instance={{...INSTANCE, hasIncident: true}}
        isInstanceModificationVisible
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTitle('Retry Instance instance_1')).toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1'),
    ).not.toBeInTheDocument();

    expect(screen.getByTitle('Modify Instance instance_1')).toBeInTheDocument();

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(await screen.findByTitle('Modify Instance instance_1'));

    expect(
      screen.queryByTitle('Retry Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Cancel Instance instance_1'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1'),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByTitle('Modify Instance instance_1'),
    ).not.toBeInTheDocument();
  });

  it('should not display modify button by default', () => {
    render(<Operations instance={{...INSTANCE, hasIncident: true}} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTitle('Retry Instance instance_1')).toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1'),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByTitle('Modify Instance instance_1'),
    ).not.toBeInTheDocument();
  });
});
