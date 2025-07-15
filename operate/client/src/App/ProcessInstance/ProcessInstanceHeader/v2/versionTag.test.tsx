/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ProcessInstanceHeader} from './index';
import {
  mockInstanceDeprecated,
  mockProcess,
  mockInstance,
  Wrapper,
} from './index.setup';
import {mockProcessXML} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcess} from 'modules/mocks/api/processes/fetchProcess';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

describe('InstanceHeader', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
    mockFetchProcessInstance().withSuccess(mockInstanceDeprecated);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
  });
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render version tag', async () => {
    mockFetchProcess().withSuccess(mockProcess);

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({
      id: mockInstanceDeprecated.id,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.getByText('Version Tag')).toBeInTheDocument();
    expect(screen.getByText(mockProcess.versionTag)).toBeInTheDocument();
  });

  it('should not render version tag', async () => {
    mockFetchProcess().withSuccess({...mockProcess, versionTag: null});

    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockInstance,
          processDefinitionVersionTag: undefined,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    processInstanceDetailsStore.init({
      id: mockInstanceDeprecated.id,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Version Tag')).not.toBeInTheDocument();
    expect(screen.queryByText(mockProcess.versionTag)).not.toBeInTheDocument();
  });

  it('should not render version tag on error', async () => {
    mockFetchProcess().withServerError();

    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockInstance,
          processDefinitionVersionTag: undefined,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    processInstanceDetailsStore.init({
      id: mockInstanceDeprecated.id,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Version Tag')).not.toBeInTheDocument();
    expect(screen.queryByText(mockProcess.versionTag)).not.toBeInTheDocument();
  });
});
