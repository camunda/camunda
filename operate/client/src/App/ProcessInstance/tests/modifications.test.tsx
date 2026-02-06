/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
  fireEvent,
} from 'modules/testing-library';
import {ProcessInstance} from '../index';
import {createUser, createvariable} from 'modules/testUtils';
import {storeStateLocally} from 'modules/utils/localStorage';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {getWrapper, mockRequests} from './mocks';
import {modificationsStore} from 'modules/stores/modifications';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockModifyProcessInstance} from 'modules/mocks/api/v2/processInstances/modifyProcessInstance';

vi.mock('modules/utils/bpmn');
vi.mock('modules/stores/process', () => ({
  processStore: {state: {process: {}}, fetchProcess: vi.fn()},
}));

describe('ProcessInstance - modification mode', () => {
  beforeEach(() => {
    mockRequests();
    modificationsStore.reset();
    mockMe().withSuccess(createUser());
  });

  it('should display the modifications header and footer when modification mode is enabled', async () => {
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      screen.queryByText('Process Instance Modification Mode'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('discard-all-button')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('apply-modifications-button'),
    ).not.toBeInTheDocument();

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    expect(
      screen.getByText('Process Instance Modification Mode'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('discard-all-button')).toBeInTheDocument();
    expect(
      screen.getByTestId('apply-modifications-button'),
    ).toBeInTheDocument();

    mockRequests();

    await user.click(screen.getByTestId('discard-all-button'));
    await user.click(
      await screen.findByRole('button', {name: /danger discard/i}),
    );

    await waitFor(() =>
      expect(
        screen.queryByText('Process Instance Modification Mode'),
      ).not.toBeInTheDocument(),
    );

    expect(screen.queryByTestId('discard-all-button')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('apply-modifications-button'),
    ).not.toBeInTheDocument();
  });

  it('should display confirmation modal when discard all is clicked during the modification mode', async () => {
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );
    await user.click(screen.getByTestId('discard-all-button'));

    expect(
      await screen.findByText(
        /about to discard all added modifications for instance/i,
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/click "discard" to proceed\./i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    await waitFor(() =>
      expect(
        screen.queryByText(
          /About to discard all added modifications for instance/,
        ),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.queryByText(/click "discard" to proceed\./i),
    ).not.toBeInTheDocument();
  });

  it('should disable apply modifications button if there are no modifications pending', async () => {
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );
    expect(screen.getByTestId('apply-modifications-button')).toBeDisabled();
  });

  it.skip('should display summary modifications modal when apply modifications is clicked during the modification mode', async () => {
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({selectableFlowNode: {flowNodeId: 'taskD'}}),
    });

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    await user.click(screen.getByRole('button', {name: 'Select flow node'}));

    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    await user.click(
      await screen.findByRole('button', {
        name: /add single flow node instance/i,
      }),
    );

    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    await user.click(screen.getByTestId('apply-modifications-button'));

    expect(
      await screen.findByText(/Planned modifications for Process Instance/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Click "Apply" to proceed./i)).toBeInTheDocument();

    expect(screen.getByText(/flow node modifications/i)).toBeInTheDocument();

    expect(
      screen.getByText('No planned variable modifications'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    await waitFor(() =>
      expect(
        screen.queryByText(/Planned modifications for Process Instance/i),
      ).not.toBeInTheDocument(),
    );
  });

  it.skip('should display loading overlay when modifications are applied', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockModifyProcessInstance().withSuccess(null);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    vi.useFakeTimers({shouldAdvanceTime: true});

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({selectableFlowNode: {flowNodeId: 'taskD'}}),
    });

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    expect(
      screen.getByText('Process Instance Modification Mode'),
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(screen.getByRole('button', {name: 'Select flow node'}));

    await user.click(
      await screen.findByRole('button', {
        name: /add single flow node instance/i,
      }),
    );

    expect(await screen.findByTestId('badge-plus-icon')).toBeInTheDocument();

    mockRequests();

    await user.click(screen.getByTestId('apply-modifications-button'));
    fireEvent.click(await screen.findByRole('button', {name: 'Apply'}));
    expect(screen.getByTestId('loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('loading-overlay'),
    );

    expect(
      screen.queryByText('Process Instance Modification Mode'),
    ).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should block navigation when modification mode is enabled', async () => {
    mockRequests();
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process "someProcessName version 1" instances/,
      }),
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('link', {
        description: /View process "someProcessName version 1" instances/,
      }),
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Leave'}));

    expect(await screen.findByText('instances page')).toBeInTheDocument();
  });
});
