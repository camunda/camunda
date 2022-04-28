/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {IncidentsWrapper} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockIncidents, mockResolvedIncidents} from './index.setup';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {incidentsStore} from 'modules/stores/incidents';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {LocationLog} from 'modules/utils/LocationLog';

jest.unmock('modules/utils/date/formatDate');
jest.mock('modules/components/IncidentOperation', () => {
  return {
    IncidentOperation: () => {
      return <div />;
    },
  };
});

jest.mock('react-transition-group', () => {
  const FakeTransition = jest.fn(({children}) => children);
  const FakeCSSTransition = jest.fn((props) =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null
  );

  return {
    CSSTransition: FakeCSSTransition,
    Transition: FakeTransition,
    TransitionGroup: jest.fn(({children}) => {
      return children.map((transition: any) => {
        const completedTransition = {...transition};
        completedTransition.props = {...transition.props, in: true};
        return completedTransition;
      });
    }),
  };
});

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route
            path="/processes/:processInstanceId"
            element={
              <>
                {children}
                <LocationLog />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('IncidentsFilter', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      )
    );

    await incidentsStore.fetchIncidents('1');
  });

  it('should render the table', async () => {
    render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    incidentsStore.setIncidentBarOpen(true);
    const table = within(screen.getByTestId('incidents-table'));

    expect(table.getByText(/^Incident Type/)).toBeInTheDocument();
    expect(table.getByText(/^Flow Node/)).toBeInTheDocument();
    expect(table.getByText(/^Job Id/)).toBeInTheDocument();
    expect(table.getByText(/^Creation Time/)).toBeInTheDocument();
    expect(table.getByText(/^Error Message/)).toBeInTheDocument();
    expect(table.getByText(/^Operations/)).toBeInTheDocument();
  });

  it('should render the filters', () => {
    render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });
    incidentsStore.setIncidentBarOpen(true);

    const errorFilters = within(screen.getByTestId(/incidents-by-errortype/i));
    const flowNodeFilters = within(
      screen.getByTestId(/incidents-by-flownode/i)
    );

    expect(errorFilters.getByText(/^condition error/i)).toBeInTheDocument();
    expect(errorFilters.getByText(/^Extract value error/)).toBeInTheDocument();
    expect(
      flowNodeFilters.getByText(/^flowNodeId_exclusiveGateway/)
    ).toBeInTheDocument();
    expect(
      flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
    ).toBeInTheDocument();
    expect(screen.getByText(/^Clear All/)).toBeInTheDocument();
  });

  describe('Filtering', () => {
    beforeEach(() => {
      incidentsStore.setIncidentBarOpen(true);
    });

    it('should not have active filters by default', () => {
      render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
        wrapper: Wrapper,
      });

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    });

    it('should filter the incidents when errorTypes are selected', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      const table = within(screen.getByTestId('incidents-table'));

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
      expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
      expect(table.getByText(/Extract value errortype/)).toBeInTheDocument();

      await user.click(
        within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
          /^Condition errortype/
        )
      );

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
      expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
      expect(
        table.queryByText(/Extract value errortype/)
      ).not.toBeInTheDocument();
    });

    it('should filter the incidents when flowNodes are selected', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      const table = within(screen.getByTestId('incidents-table'));

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
      expect(
        table.getByText(/flowNodeId_exclusiveGateway/)
      ).toBeInTheDocument();
      expect(
        table.getByText(/flowNodeId_alwaysFailingTask/)
      ).toBeInTheDocument();

      await user.click(
        within(screen.getByTestId(/incidents-by-flownode/i)).getByText(
          /^flowNodeId_exclusiveGateway/
        )
      );

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
      expect(
        table.getByText(/flowNodeId_exclusiveGateway/)
      ).toBeInTheDocument();
      expect(
        table.queryByText(/flowNodeId_alwaysFailingTask/)
      ).not.toBeInTheDocument();
    });

    it('should filter the incidents when both errorTypes & flowNodes are selected', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
      await user.click(
        within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
          /^Condition errortype/
        )
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);

      const flowNodeFilters = within(
        screen.getByTestId(/incidents-by-flownode/i)
      );

      await user.click(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      );
      expect(screen.queryAllByLabelText(/^incident/i)).toHaveLength(0);
      await user.click(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
    });

    it('should remove filter when only related incident gets resolved', async () => {
      const {user, rerender} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

      const flowNodeFilters = within(
        screen.getByTestId(/incidents-by-flownode/i)
      );

      await user.click(
        flowNodeFilters.getByText(/^flowNodeId_exclusiveGateway/)
      );
      await user.click(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

      // incident is resolved
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/incidents',
          (_, res, ctx) => res.once(ctx.json(mockResolvedIncidents))
        )
      );

      await incidentsStore.fetchIncidents('1');

      rerender(<IncidentsWrapper setIsInTransition={jest.fn()} />);

      expect(
        flowNodeFilters.queryByText(/^flowNodeId_exclusiveGateway/)
      ).not.toBeInTheDocument();
      expect(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      ).toBeInTheDocument();
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
    });

    it('should drop all filters when clicking the clear all button', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

      await user.click(
        within(screen.getByTestId(/incidents-by-flownode/i)).getByText(
          /^flowNodeId_exclusiveGateway/
        )
      );
      await user.click(
        within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
          /^Condition errortype/
        )
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);

      await user.click(screen.getByText(/^Clear All/));

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    });
  });

  describe('Sorting', () => {
    beforeEach(() => {
      incidentsStore.setIncidentBarOpen(true);
    });

    it('should sort by incident type', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      let [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/Extract value errortype/);
      expect(secondRow).toHaveTextContent(/Condition errortype/);

      await user.click(
        screen.getByRole('button', {
          name: /sort by errortype/i,
        })
      );

      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?sort=errorType%2Basc$/
      );

      [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/Condition errortype/);
      expect(secondRow).toHaveTextContent(/Extract value errortype/);

      await user.click(
        screen.getByRole('button', {
          name: /sort by errortype/i,
        })
      );

      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?sort=errorType%2Bdesc$/
      );

      [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/Extract value errortype/);
      expect(secondRow).toHaveTextContent(/Condition errortype/);
    });

    it('should sort by flow node', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      let [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);
      expect(secondRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);

      await user.click(
        screen.getByRole('button', {
          name: /sort by flownodename/i,
        })
      );

      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?sort=flowNodeName%2Bdesc$/
      );

      [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);
      expect(secondRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);

      await user.click(
        screen.getByRole('button', {
          name: /sort by flowNodeName/i,
        })
      );

      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?sort=flowNodeName%2Basc$/
      );

      [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);
      expect(secondRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);
    });

    it('should sort by creation time', async () => {
      const {user} = render(
        <IncidentsWrapper setIsInTransition={jest.fn()} />,
        {
          wrapper: Wrapper,
        }
      );

      let [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);
      expect(secondRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);

      await user.click(
        screen.getByRole('button', {
          name: /sort by creationTime/i,
        })
      );

      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?sort=creationTime%2Bdesc$/
      );

      [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/2022-03-01 14:26:19/);
      expect(secondRow).toHaveTextContent(/2019-03-01 14:26:19/);

      await user.click(
        screen.getByRole('button', {
          name: /sort by creationTime/i,
        })
      );

      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?sort=creationTime%2Basc$/
      );

      [, firstRow, secondRow] = screen.getAllByRole('row');
      expect(firstRow).toHaveTextContent(/2019-03-01 14:26:19/);
      expect(secondRow).toHaveTextContent(/2022-03-01 14:26:19/);
    });
  });
});
