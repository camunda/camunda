/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, render, screen} from 'modules/testing-library';
import {ModificationIcons} from './index';
import {modificationsStore} from 'modules/stores/modifications';
import {useEffect} from 'react';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {cancelAllTokens} from 'modules/utils/modifications';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {open} from 'modules/mocks/diagrams';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return modificationsStore.reset;
  }, []);

  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

describe('<ModificationIcons />', () => {
  beforeEach(() => {
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'parent_sub_process',
          active: 3,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
        {
          elementId: 'inner_sub_process',
          active: 3,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
        {
          elementId: 'user_task',
          active: 3,
          incidents: 0,
          completed: 0,
          canceled: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
  });

  it('should show correct icons for modifications planning to be added', () => {
    render(
      <ModificationIcons
        elementId="some-element-id"
        endDate={null}
        isPlaceholder={true}
        scopeKeyHierarchy={[
          'some-other-parent-element-id',
          'some-parent-element-id',
          'some-element-id',
        ]}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getByTitle(
        'Ensure to add/edit variables if required, input/output mappings are not executed during modification',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByTitle('This element instance is planned to be added'),
    ).toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if all the running tokens on the element is canceled', async () => {
    render(
      <ModificationIcons
        elementId="user_task"
        isPlaceholder={false}
        endDate={null}
        scopeKeyHierarchy={[
          'some-other-parent-element-id',
          'some-parent-element-id',
          'some-element-id',
        ]}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByTitle('This element instance is planned to be canceled'),
    ).not.toBeInTheDocument();

    act(() => cancelAllTokens('user_task', 0, 0, {}));

    expect(
      screen.getByTitle('This element instance is planned to be canceled'),
    ).toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if one of the running tokens on the element is canceled', async () => {
    render(
      <ModificationIcons
        elementId="user_task"
        isPlaceholder={false}
        endDate={null}
        scopeKeyHierarchy={[
          'some-other-parent-element-id',
          'some-parent-element-id',
          'some-element-id',
        ]}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken('user_task', 'some-element-id', {}),
    );

    expect(
      screen.getByTitle('This element instance is planned to be canceled'),
    ).toBeInTheDocument();
  });

  it('should not show modification planned to be canceled icon if one of the other running tokens on the element is canceled', async () => {
    render(
      <ModificationIcons
        elementId="user_task"
        isPlaceholder={false}
        endDate={null}
        scopeKeyHierarchy={[
          'some-other-parent-element-id',
          'some-parent-element-id',
          'some-element-id',
        ]}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken('user_task', 'some-other-element-id', {}),
    );

    expect(
      screen.queryByTitle('This element instance is planned to be canceled'),
    ).not.toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if one of the parent running tokens on the element is canceled', async () => {
    render(
      <ModificationIcons
        elementId="user_task"
        isPlaceholder={false}
        endDate={null}
        scopeKeyHierarchy={[
          'some-other-parent-element-id',
          'some-parent-element-id',
          'some-element-id',
        ]}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken('user_task', 'some-parent-element-id', {}),
    );

    expect(
      screen.getByTitle('This element instance is planned to be canceled'),
    ).toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if one of the other parent running tokens on the element is canceled', async () => {
    render(
      <ModificationIcons
        elementId="user_task"
        isPlaceholder={false}
        endDate={null}
        scopeKeyHierarchy={[
          'some-other-parent-element-id',
          'some-parent-element-id',
          'some-element-id',
        ]}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken(
        'user_task',
        'some-second-parent-element-id',
        {},
      ),
    );

    expect(
      screen.queryByTitle('This element instance is planned to be canceled'),
    ).not.toBeInTheDocument();
  });
});
