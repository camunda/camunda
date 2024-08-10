/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {CollapsiblePanel} from './index';
import {MemoryRouter} from 'react-router-dom';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse, http} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {createMockProcess} from 'modules/queries/useProcesses';

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<CollapsiblePanel />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );
  });

  it('should render a collapsed panel', () => {
    render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Filter tasks'}),
    ).toBeInTheDocument();
  });

  it('should add custom filter from collapsed panel', async () => {
    nodeMockServer.use(
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
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByRole('dialog', {name: /custom filters modal/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Filter tasks'}));

    expect(
      screen.getByRole('dialog', {name: /custom filters modal/i}),
    ).toBeVisible();
  });

  it('should render an expanded panel', async () => {
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    expect(screen.getByRole('button', {name: 'Collapse'})).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'All open tasks'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'Assigned to me'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'Unassigned'})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'Completed'})).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Custom'}),
    ).not.toBeInTheDocument();
  });

  it('should render custom filters link', async () => {
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    expect(screen.getByRole('link', {name: 'Custom'})).toBeInTheDocument();
  });

  it('should render links correctly', async () => {
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
      'custom-1': {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
        name: 'Custom-1',
      },
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    await user.click(screen.getByRole('link', {name: 'Completed'}));

    expect(screen.getByRole('link', {name: 'All open tasks'})).toHaveAttribute(
      'href',
      '/?filter=all-open',
    );
    expect(screen.getByRole('link', {name: 'Assigned to me'})).toHaveAttribute(
      'href',
      '/?filter=assigned-to-me',
    );
    expect(screen.getByRole('link', {name: 'Unassigned'})).toHaveAttribute(
      'href',
      '/?filter=unassigned',
    );
    expect(screen.getByRole('link', {name: 'Completed'})).toHaveAttribute(
      'href',
      '/?filter=completed&sortBy=completion',
    );
    expect(screen.getByRole('link', {name: 'Custom'})).toHaveAttribute(
      'href',
      '/?filter=custom&state=COMPLETED&processDefinitionKey=process-1',
    );
    expect(screen.getByRole('link', {name: 'Custom-1'})).toHaveAttribute(
      'href',
      '/?filter=custom-1&state=COMPLETED&processDefinitionKey=process-1',
    );
  });

  it('should not erase existing search params', async () => {
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(['/tasks?filter=completed&foo=bar']),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    expect(screen.getByRole('link', {name: 'Completed'})).toHaveAttribute(
      'href',
      '/tasks?filter=completed&foo=bar&sortBy=completion',
    );
  });

  it('should allow sort by completion date for completed and custom filters only', async () => {
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(['/?filter=completed&sortBy=completion']),
    });

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    expect(screen.getByRole('link', {name: 'All open tasks'})).toHaveAttribute(
      'href',
      '/?filter=all-open',
    );
    expect(screen.getByRole('link', {name: 'Assigned to me'})).toHaveAttribute(
      'href',
      '/?filter=assigned-to-me',
    );
    expect(screen.getByRole('link', {name: 'Unassigned'})).toHaveAttribute(
      'href',
      '/?filter=unassigned',
    );
    expect(screen.getByRole('link', {name: 'Completed'})).toHaveAttribute(
      'href',
      '/?filter=completed&sortBy=completion',
    );
    expect(screen.getByRole('link', {name: 'Custom'})).toHaveAttribute(
      'href',
      '/?filter=custom&state=COMPLETED&processDefinitionKey=process-1',
    );
  });

  it('should allow to delete custom filters', async () => {
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );
    await user.click(
      screen.getByRole('button', {name: /custom filter actions/i}),
    );
    await user.click(screen.getByText(/^delete$/i));
    await user.click(
      within(screen.getByTestId('direct-delete-filter-modal')).getByRole(
        'button',
        {name: /confirm deletion/i},
      ),
    );

    expect(
      screen.queryByRole('link', {name: 'Custom'}),
    ).not.toBeInTheDocument();
  });

  it('should allow to edit custom filters', async () => {
    nodeMockServer.use(
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
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    await user.click(
      screen.getByRole('button', {name: /custom filter actions/i}),
    );

    expect(
      screen.queryByRole('dialog', {name: /custom filters modal/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByText(/edit/i));

    expect(
      screen.getByRole('dialog', {name: /custom filters modal/i}),
    ).toBeVisible();
    expect(
      screen.getByRole('radio', {
        name: /completed/i,
      }),
    ).toBeChecked();
  });

  it('should allow to create custom filters', async () => {
    nodeMockServer.use(
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
    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    expect(
      screen.queryByRole('link', {name: 'Custom'}),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole('dialog', {name: /custom filters modal/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /new filter/i}));

    expect(
      screen.getByRole('dialog', {name: /custom filters modal/i}),
    ).toBeVisible();
  });

  it('should allow to delete custom filters', async () => {
    nodeMockServer.use(
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

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
    });

    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );

    expect(
      screen.queryByRole('dialog', {name: /delete filter/i}),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /custom filter actions/i}),
    );
    await user.click(screen.getByText(/^delete$/i));

    expect(screen.getByRole('dialog', {name: /delete filter/i})).toBeVisible();

    await user.click(
      within(screen.getByRole('dialog', {name: /delete filter/i})).getByRole(
        'button',
        {
          name: /danger confirm deletion/i,
        },
      ),
    );

    expect(
      screen.queryByRole('heading', {
        name: /the custom filter will be deleted\./i,
      }),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole('link', {name: 'Custom'}),
    ).not.toBeInTheDocument();
    expect(getStateLocally('customFilters')).toEqual({});
  });

  it('should allow to create custom persistable filters', async () => {
    nodeMockServer.use(
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

    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );
    await user.click(screen.getByRole('button', {name: /new filter/i}));

    expect(
      screen.getByRole('dialog', {name: /custom filters modal/i}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('radio', {name: /completed/i}));

    expect(
      screen.queryByRole('textbox', {
        name: /filter name/i,
      }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /save/i}));
    await user.type(
      screen.getByRole('textbox', {
        name: /filter name/i,
      }),
      'A new filter',
    );
    await user.click(screen.getByRole('button', {name: /save and apply/i}));

    expect(
      screen.queryByRole('textbox', {
        name: /filter name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('dialog', {name: /custom filters modal/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'A new filter'}),
    ).toBeInTheDocument();
  });

  it('should allow to edit custom persistable filters', async () => {
    nodeMockServer.use(
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

    storeStateLocally('customFilters', {
      'custom-1': {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
        name: 'A custom filter',
      },
    });

    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );
    await user.click(
      screen.getByRole('button', {name: /custom filter actions/i}),
    );
    await user.click(screen.getByText(/^edit/i));

    expect(
      screen.getByRole('dialog', {name: /custom filters modal/i}),
    ).toBeVisible();
    expect(screen.getByRole('radio', {name: /completed/i})).toBeChecked();
    expect(
      screen.getByRole('textbox', {
        name: /filter name/i,
      }),
    ).toHaveValue('A custom filter');

    await user.clear(screen.getByRole('textbox', {name: /filter name/i}));
    await user.type(
      screen.getByRole('textbox', {name: /filter name/i}),
      'A new filter name',
    );
    await user.click(screen.getByRole('button', {name: /save and apply/i}));

    expect(
      screen.queryByRole('dialog', {name: /custom filters modal/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'A new filter name'}),
    ).toBeInTheDocument();
    expect(getStateLocally('customFilters')).toEqual({
      'custom-1': {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
        name: 'A new filter name',
      },
    });
  });

  it('should allow to persist custom filter', async () => {
    nodeMockServer.use(
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

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
    });

    const {user} = render(<CollapsiblePanel />, {
      wrapper: createWrapper(),
    });

    await user.click(
      screen.getByRole('button', {name: 'Expand to show filters'}),
    );
    await user.click(
      screen.getByRole('button', {name: /custom filter actions/i}),
    );
    await user.click(screen.getByText(/^edit$/i));
    await user.click(screen.getByRole('button', {name: /save/i}));
    await user.type(
      screen.getByRole('textbox', {
        name: /filter name/i,
      }),
      'A new filter name',
    );
    await user.click(screen.getByRole('button', {name: /save and apply/i}));

    expect(
      screen.queryByRole('dialog', {name: /custom filters modal/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'A new filter name'}),
    ).toBeInTheDocument();
    expect(Object.values(getStateLocally('customFilters')!)).toEqual([
      {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
      },
      {
        status: 'completed',
        assignee: 'all',
        bpmnProcess: 'process-1',
        name: 'A new filter name',
      },
    ]);
  });
});
