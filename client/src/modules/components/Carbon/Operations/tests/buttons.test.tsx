/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {storeStateLocally} from 'modules/utils/localStorage';
import {Operations} from '../index';
import {INSTANCE, Wrapper} from './mocks';

describe('Operations - Buttons', () => {
  it('should render retry, cancel and modify buttons if instance is running and has an incident', () => {
    render(
      <Operations
        instance={{...INSTANCE, state: 'INCIDENT'}}
        isInstanceModificationVisible
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getByTitle('Retry Instance instance_1')).toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1')
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
        wrapper: Wrapper,
      }
    );

    expect(
      screen.queryByTitle('Retry Instance instance_1')
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1')
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
      {wrapper: Wrapper}
    );

    expect(
      screen.queryByTitle('Retry Instance instance_1')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Cancel Instance instance_1')
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Delete Instance instance_1')).toBeInTheDocument();

    expect(
      screen.queryByTitle('Modify Instance instance_1')
    ).not.toBeInTheDocument();
  });

  it('should render delete button if instance is canceled', () => {
    render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'CANCELED',
        }}
        isInstanceModificationVisible
      />,
      {wrapper: Wrapper}
    );

    expect(
      screen.queryByTitle('Retry Instance instance_1')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Cancel Instance instance_1')
    ).not.toBeInTheDocument();
    expect(screen.getByTitle('Delete Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Modify Instance instance_1')
    ).not.toBeInTheDocument();
  });

  it('should hide operation buttons in process instance modification mode', async () => {
    const {user} = render(
      <Operations
        instance={{...INSTANCE, state: 'INCIDENT'}}
        isInstanceModificationVisible
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getByTitle('Retry Instance instance_1')).toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1')
    ).not.toBeInTheDocument();

    expect(screen.getByTitle('Modify Instance instance_1')).toBeInTheDocument();

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(await screen.findByTitle('Modify Instance instance_1'));

    expect(
      screen.queryByTitle('Retry Instance instance_1')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Cancel Instance instance_1')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1')
    ).not.toBeInTheDocument();

    expect(
      screen.queryByTitle('Modify Instance instance_1')
    ).not.toBeInTheDocument();
  });

  it('should not display modify button by default', () => {
    render(<Operations instance={{...INSTANCE, state: 'INCIDENT'}} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTitle('Retry Instance instance_1')).toBeInTheDocument();
    expect(screen.getByTitle('Cancel Instance instance_1')).toBeInTheDocument();
    expect(
      screen.queryByTitle('Delete Instance instance_1')
    ).not.toBeInTheDocument();

    expect(
      screen.queryByTitle('Modify Instance instance_1')
    ).not.toBeInTheDocument();
  });
});
