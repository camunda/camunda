/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
});
