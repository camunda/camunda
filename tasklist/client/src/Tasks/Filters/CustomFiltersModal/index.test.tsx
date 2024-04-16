/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {CustomFiltersModal} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {HttpResponse, http} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {createMockProcess} from 'modules/queries/useProcesses';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<CustomFiltersModal />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/processes',
        () => {
          return HttpResponse.json([
            createMockProcess('process-0'),
            createMockProcess('process-1'),
          ]);
        },
        {
          once: true,
        },
      ),
    );
  });

  it('should render filters dialog', async () => {
    render(
      <CustomFiltersModal isOpen onClose={() => {}} onApply={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.getByRole('heading', {name: /apply filters/i}),
    ).toBeInTheDocument();
    const assigneeGroup = screen.getByRole('group', {name: /assignee/i});
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

    const statusGroup = screen.getByRole('group', {name: /status/i});
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

    const processesCombobox = screen.getByRole('combobox', {name: /process/i});
    expect(processesCombobox).toBeInTheDocument();
    await waitFor(() => expect(processesCombobox).toBeEnabled());
    expect(
      within(processesCombobox).getByRole('option', {
        name: /all processes/i,
      }),
    ).toBeInTheDocument();
    expect(
      within(processesCombobox).getByRole('option', {
        name: /process process-0/i,
      }),
    ).toBeInTheDocument();
    expect(
      within(processesCombobox).getByRole('option', {
        name: /process process-1/i,
      }),
    ).toBeInTheDocument();

    const advancedFiltersSwitch = screen.getByRole('switch', {
      name: /advanced filters/i,
    });
    expect(advancedFiltersSwitch).toBeInTheDocument();
    expect(advancedFiltersSwitch).not.toBeChecked();

    expect(screen.getByRole('button', {name: /reset/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /cancel/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();
  });

  it('should render user and group filters', async () => {
    const {user} = render(
      <CustomFiltersModal isOpen onClose={() => {}} onApply={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.queryByRole('textbox', {name: /assigned to user/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /in a group/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('radio', {name: /user and group/i}));

    expect(
      screen.getByRole('textbox', {name: /assigned to user/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {name: /in a group/i}),
    ).toBeInTheDocument();
  });

  it('should render advanced filters', async () => {
    const {user} = render(
      <CustomFiltersModal isOpen onClose={() => {}} onApply={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.queryByRole('group', {name: /due date/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /from/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /to/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /follow up date/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /task id/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('group', {name: /task variables/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('switch', {name: /advanced filters/i}));

    const dueDateGroup = screen.getByRole('group', {name: /due date/i});
    expect(dueDateGroup).toBeInTheDocument();
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /from/i}),
    ).toBeInTheDocument();
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /to/i}),
    ).toBeInTheDocument();

    const followUpDateGroup = screen.getByRole('group', {
      name: /follow up date/i,
    });
    expect(followUpDateGroup).toBeInTheDocument();
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /from/i}),
    ).toBeInTheDocument();
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /to/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', {name: /task id/i})).toBeInTheDocument();
    expect(
      screen.getByRole('group', {name: /task variables/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('textbox', {name: /name/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {name: /value/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /remove variable/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(screen.getByRole('textbox', {name: /name/i})).toBeInTheDocument();
    expect(screen.getByRole('textbox', {name: /value/i})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /remove variable/i}),
    ).toBeInTheDocument();
  });

  it('should dispatch event handlers', async () => {
    const mockOnClose = vi.fn();
    const mockOnApply = vi.fn();

    const {user} = render(
      <CustomFiltersModal isOpen onClose={mockOnClose} onApply={mockOnApply} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(mockOnClose).not.toHaveBeenCalled();
    expect(mockOnApply).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(mockOnClose).toHaveBeenCalledOnce();
    expect(mockOnApply).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnClose).toHaveBeenCalledOnce();
    expect(mockOnApply).toHaveBeenCalledOnce();
  });

  it('should load user groups', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUserWithGroups);
        },
        {once: true},
      ),
    );
    const {user} = render(
      <CustomFiltersModal isOpen onClose={() => {}} onApply={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(screen.getByRole('radio', {name: /user and group/i}));

    expect(
      await screen.findByRole('combobox', {name: /in a group/i}),
    ).toBeInTheDocument();
  });

  it('should load from previously saved filters', async () => {
    const mockDate = new Date('2022-01-01');
    storeStateLocally('customFilters', {
      custom: {
        assignee: 'me',
        status: 'completed',
        bpmnProcess: 'process-0',
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
      <CustomFiltersModal isOpen onClose={() => {}} onApply={() => {}} />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: /process/i})).toHaveValue(
        'process-0',
      ),
    );
    expect(screen.getByRole('radio', {name: /me/i})).toBeChecked();
    expect(screen.getByRole('radio', {name: /completed/i})).toBeChecked();

    const dueDateGroup = screen.getByRole('group', {name: /due date/i});
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /from/i}),
    ).toHaveValue('01/01/22');
    expect(
      within(dueDateGroup).getByRole('textbox', {name: /to/i}),
    ).toHaveValue('01/01/22');

    const followUpDateGroup = screen.getByRole('group', {name: /due date/i});
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /from/i}),
    ).toHaveValue('01/01/22');
    expect(
      within(followUpDateGroup).getByRole('textbox', {name: /to/i}),
    ).toHaveValue('01/01/22');

    expect(screen.getByRole('textbox', {name: /task id/i})).toHaveValue(
      'task-0',
    );

    const variablesGroup = screen.getByRole('group', {name: /task variables/i});

    expect(
      within(variablesGroup).getByRole('textbox', {name: /name/i}),
    ).toHaveValue('variable-0');
    expect(
      within(variablesGroup).getByRole('textbox', {name: /value/i}),
    ).toHaveValue('"value-0"');
  });

  it('should submit filters', async () => {
    const mockOnApply = vi.fn();
    const submitValues = {
      assignee: 'unassigned',
      status: 'open',
      bpmnProcess: 'process-1',
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
      <CustomFiltersModal isOpen onClose={() => {}} onApply={mockOnApply} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(getStateLocally('customFilters')).toBeNull();

    await user.click(screen.getByRole('radio', {name: /unassigned/i}));
    await user.click(screen.getByRole('radio', {name: /open/i}));
    await user.selectOptions(
      screen.getByRole('combobox', {name: /process/i}),
      'process-1',
    );
    await user.click(screen.getByRole('switch', {name: /advanced filters/i}));
    const dueDateGroup = screen.getByRole('group', {name: /due date/i});
    await user.type(
      within(dueDateGroup).getByRole('textbox', {name: /from/i}),
      '01/01/22',
    );
    await user.type(
      within(dueDateGroup).getByRole('textbox', {name: /to/i}),
      '01/01/22',
    );
    const followUpDateGroup = screen.getByRole('group', {
      name: /follow up date/i,
    });
    await user.type(
      within(followUpDateGroup).getByRole('textbox', {name: /from/i}),
      '01/01/22',
    );
    await user.type(
      within(followUpDateGroup).getByRole('textbox', {name: /to/i}),
      '01/01/22',
    );
    await user.type(screen.getByRole('textbox', {name: /task id/i}), 'task-0');
    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await user.type(screen.getByRole('textbox', {name: /name/i}), 'variable-0');
    await user.type(screen.getByRole('textbox', {name: /value/i}), '"value-0"');

    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnApply).toHaveBeenCalledOnce();
    expect(mockOnApply).toHaveBeenLastCalledWith(submitValues);
    expect(getStateLocally('customFilters')).toEqual({
      custom: submitValues,
    });
  });
});
