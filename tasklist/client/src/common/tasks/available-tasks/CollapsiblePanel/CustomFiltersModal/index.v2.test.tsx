/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'common/testing/testing-library';
import {CustomFiltersModal} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {HttpResponse, http} from 'msw';
import * as userMocks from 'common/mocks/current-user';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {endpoints} from '@vzeta/camunda-api-zod-schemas/operate';
import {
  getProcessDefinitionMock,
  getQueryProcessDefinitionsResponseMock,
} from 'v2/mocks/processDefinitions';

const definitionsMock = getQueryProcessDefinitionsResponseMock([
  getProcessDefinitionMock(),
  getProcessDefinitionMock(),
]);

vi.mock('common/config/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('common/config/getClientConfig')>();
  return {
    getClientConfig() {
      return {
        ...actual.getClientConfig(),
        clientMode: 'v2',
      };
    },
  };
});

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>{children}</QueryClientProvider>
  );

  return Wrapper;
};

describe('<CustomFiltersModal />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        endpoints.queryProcessDefinitions.getUrl(),
        () => {
          return HttpResponse.json(definitionsMock);
        },
        {
          once: true,
        },
      ),
    );
  });

  it('should render filters dialog', async () => {
    render(
      <CustomFiltersModal
        isOpen
        onClose={() => {}}
        onSuccess={() => {}}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    expect(
      within(dialog).getByRole('heading', {name: /apply filters/i}),
    ).toBeInTheDocument();
    const assigneeGroup = within(dialog).getByRole('group', {
      name: /assignee/i,
    });
    expect(assigneeGroup).toBeInTheDocument();
    expect(
      within(assigneeGroup).getByRole('radio', {name: /all/i}),
    ).toBeInTheDocument();
    expect(
      within(assigneeGroup).getByRole('radio', {name: /unassigned/i}),
    ).toBeInTheDocument();
    expect(
      within(assigneeGroup).getByRole('radio', {name: /me/i}),
    ).toBeInTheDocument();
    expect(
      within(assigneeGroup).getByRole('radio', {name: /user and group/i}),
    ).toBeInTheDocument();

    const statusGroup = within(dialog).getByRole('group', {name: /status/i});
    expect(statusGroup).toBeInTheDocument();
    expect(
      within(statusGroup).getByRole('radio', {name: /all/i}),
    ).toBeInTheDocument();
    expect(
      within(statusGroup).getByRole('radio', {name: /open/i}),
    ).toBeInTheDocument();
    expect(
      within(statusGroup).getByRole('radio', {name: /completed/i}),
    ).toBeInTheDocument();

    const processesCombobox = within(dialog).getByRole('combobox', {
      name: /process/i,
    });
    expect(processesCombobox).toBeInTheDocument();
    await waitFor(() => expect(processesCombobox).toBeEnabled());
    expect(
      within(processesCombobox).getByRole('option', {
        name: /all processes/i,
      }),
    ).toBeInTheDocument();
    expect(
      within(processesCombobox).getByRole('option', {
        name: /process 0/i,
      }),
    ).toBeInTheDocument();
    expect(
      within(processesCombobox).getByRole('option', {
        name: /process 1/i,
      }),
    ).toBeInTheDocument();

    const advancedFiltersSwitch = within(dialog).getByRole('switch', {
      name: /advanced filters/i,
    });
    expect(advancedFiltersSwitch).toBeInTheDocument();
    expect(advancedFiltersSwitch).not.toBeChecked();

    expect(
      within(dialog).getByRole('button', {name: /reset/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', {name: /cancel/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', {name: /apply/i}),
    ).toBeInTheDocument();
  });

  it('should render user and group filters', async () => {
    const {user} = render(
      <CustomFiltersModal
        isOpen
        onClose={() => {}}
        onSuccess={() => {}}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    expect(
      within(dialog).queryByRole('textbox', {name: /assigned to user/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('textbox', {name: /in a group/i}),
    ).not.toBeInTheDocument();

    await user.click(
      within(dialog).getByRole('radio', {name: /user and group/i}),
    );

    expect(
      within(dialog).getByRole('textbox', {name: /assigned to user/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('textbox', {name: /in a group/i}),
    ).toBeInTheDocument();
  });

  it('should render advanced filters', async () => {
    const {user} = render(
      <CustomFiltersModal
        isOpen
        onClose={() => {}}
        onSuccess={() => {}}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    expect(
      within(dialog).queryByRole('group', {name: /due date/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('textbox', {name: /from/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('textbox', {name: /to/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('textbox', {name: /follow up date/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('textbox', {name: /task id/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('group', {name: /task variables/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(
      within(dialog).getByRole('switch', {name: /advanced filters/i}),
    );

    const dueDateGroup = within(dialog).getByRole('group', {name: /due date/i});
    expect(dueDateGroup).toBeInTheDocument();
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /from/i}),
    ).toBeInTheDocument();
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /to/i}),
    ).toBeInTheDocument();

    const followUpDateGroup = within(dialog).getByRole('group', {
      name: /follow up date/i,
    });
    expect(followUpDateGroup).toBeInTheDocument();
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /from/i}),
    ).toBeInTheDocument();
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /to/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('textbox', {name: /task id/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('group', {name: /task variables/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    expect(
      within(dialog).queryByRole('textbox', {name: /name/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('textbox', {name: /value/i}),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole('button', {name: /remove variable/i}),
    ).not.toBeInTheDocument();

    await user.click(
      within(dialog).getByRole('button', {name: /add variable/i}),
    );

    expect(
      within(dialog).getByRole('textbox', {name: /name/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('textbox', {name: /value/i}),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', {name: /remove variable/i}),
    ).toBeInTheDocument();
  });

  it('should dispatch event handlers', async () => {
    const mockOnClose = vi.fn();
    const mockOnSuccess = vi.fn();

    const {user} = render(
      <CustomFiltersModal
        isOpen
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    expect(mockOnClose).not.toHaveBeenCalled();
    expect(mockOnSuccess).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', {name: /cancel/i}));

    expect(mockOnClose).toHaveBeenCalledOnce();
    expect(mockOnSuccess).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', {name: /apply/i}));

    expect(mockOnClose).toHaveBeenCalledOnce();
    expect(mockOnSuccess).toHaveBeenCalledOnce();
  });

  it('should load user groups', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUserWithGroups);
        },
        {once: true},
      ),
    );
    const {user} = render(
      <CustomFiltersModal
        isOpen
        onClose={() => {}}
        onSuccess={() => {}}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    await user.click(
      within(dialog).getByRole('radio', {name: /user and group/i}),
    );

    expect(
      await within(dialog).findByRole('combobox', {name: /in a group/i}),
    ).toBeInTheDocument();
  });

  it('should load from previously saved filters', async () => {
    const mockDate = new Date('2022-01-01');
    storeStateLocally('customFilters', {
      custom: {
        assignee: 'me',
        status: 'completed',
        bpmnProcess: '0',
        dueDateFrom: mockDate,
        dueDateTo: mockDate,
        followUpDateFrom: mockDate,
        followUpDateTo: mockDate,
        taskId: 'task-0',
        variables: [
          {
            name: 'variable-0',
            value: '"value-0"',
          },
        ],
      },
    });

    render(
      <CustomFiltersModal
        isOpen
        filterId="custom"
        onClose={() => {}}
        onSuccess={() => {}}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    await waitFor(() =>
      expect(
        within(dialog).getByRole('combobox', {name: /process/i}),
      ).toHaveValue('0'),
    );
    expect(within(dialog).getByRole('radio', {name: /me/i})).toBeChecked();
    expect(
      within(dialog).getByRole('radio', {name: /completed/i}),
    ).toBeChecked();

    const dueDateGroup = within(dialog).getByRole('group', {name: /due date/i});
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /from/i}),
    ).toHaveValue('01/01/2022');
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /to/i}),
    ).toHaveValue('01/01/2022');

    const followUpDateGroup = within(dialog).getByRole('group', {
      name: /due date/i,
    });
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /from/i}),
    ).toHaveValue('01/01/2022');
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /to/i}),
    ).toHaveValue('01/01/2022');

    expect(within(dialog).getByRole('textbox', {name: /task id/i})).toHaveValue(
      'task-0',
    );

    const variablesGroup = within(dialog).getByRole('group', {
      name: /task variables/i,
    });

    expect(
      within(variablesGroup).getByRole('textbox', {name: /name/i}),
    ).toHaveValue('variable-0');
    expect(
      within(variablesGroup).getByRole('textbox', {name: /value/i}),
    ).toHaveValue('"value-0"');
  });

  it('should submit filters', async () => {
    const mockOnSuccess = vi.fn();
    const submitValues = {
      assignee: 'unassigned',
      status: 'open',
      bpmnProcess: '0',
      dueDateFrom: new Date('2022-01-01'),
      dueDateTo: new Date('2022-01-01'),
      followUpDateFrom: new Date('2022-01-01'),
      followUpDateTo: new Date('2022-01-01'),
      taskId: 'task-0',
      variables: [
        {
          name: 'variable-0',
          value: '"value-0"',
        },
      ],
    } as const;

    const {user} = render(
      <CustomFiltersModal
        isOpen
        filterId="custom"
        onClose={() => {}}
        onSuccess={mockOnSuccess}
        onDelete={() => {}}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});

    expect(getStateLocally('customFilters')).toBeNull();

    await user.click(within(dialog).getByRole('radio', {name: /unassigned/i}));
    await user.click(within(dialog).getByRole('radio', {name: /open/i}));
    await user.selectOptions(
      within(dialog).getByRole('combobox', {name: /process/i}),
      '0',
    );
    await user.click(
      within(dialog).getByRole('switch', {name: /advanced filters/i}),
    );
    const dueDateGroup = within(dialog).getByRole('group', {name: /due date/i});
    await user.type(
      within(dueDateGroup).getByRole('textbox', {name: /from/i}),
      '01/01/2022',
    );
    await user.type(
      within(dueDateGroup).getByRole('textbox', {name: /to/i}),
      '01/01/2022',
    );
    const followUpDateGroup = within(dialog).getByRole('group', {
      name: /follow up date/i,
    });
    await user.type(
      within(followUpDateGroup).getByRole('textbox', {name: /from/i}),
      '01/01/2022',
    );
    await user.type(
      within(followUpDateGroup).getByRole('textbox', {name: /to/i}),
      '01/01/2022',
    );
    await user.type(
      within(dialog).getByRole('textbox', {name: /task id/i}),
      'task-0',
    );
    await user.click(
      within(dialog).getByRole('button', {name: /add variable/i}),
    );
    await user.type(
      within(dialog).getByRole('textbox', {name: /name/i}),
      'variable-0',
    );
    await user.type(
      within(dialog).getByRole('textbox', {name: /value/i}),
      '"value-0"',
    );

    await user.click(within(dialog).getByRole('button', {name: /apply/i}));

    expect(mockOnSuccess).toHaveBeenCalledOnce();
    expect(mockOnSuccess).toHaveBeenLastCalledWith('custom');
    expect(getStateLocally('customFilters')).toEqual({
      custom: submitValues,
    });
  });
});
