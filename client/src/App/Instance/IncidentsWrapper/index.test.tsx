/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {IncidentsWrapper as IncidentsWrapperNext} from './index';
import {IncidentsWrapper as IncidentsWrapperLegacy} from './index.legacy';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {
  mockIncidentWrapperProps,
  mockIncidents,
  mockIncidentsLegacy,
  mockResolvedIncidents,
  mockResolvedIncidentsLegacy,
} from './index.setup';

import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {incidentsStore as incidentsStoreNext} from 'modules/stores/incidents';
import {incidentsStore as incidentsStoreLegacy} from 'modules/stores/incidents.legacy';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {IS_NEXT_INCIDENTS} from 'modules/feature-flags';

const incidentsStore = IS_NEXT_INCIDENTS
  ? incidentsStoreNext
  : incidentsStoreLegacy;

const IncidentsWrapper = IS_NEXT_INCIDENTS
  ? IncidentsWrapperNext
  : IncidentsWrapperLegacy;

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

const history = createMemoryHistory({initialEntries: ['/instances/1']});

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <Router history={history}>
        <Route path="/instances/:processInstanceId">{children}</Route>
      </Router>
    </ThemeProvider>
  );
};

describe('IncidentsFilter', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json(IS_NEXT_INCIDENTS ? mockIncidents : mockIncidentsLegacy)
        )
      )
    );

    await incidentsStore.fetchIncidents('1');
  });

  it('should render the IncidentsBanner', () => {
    render(<IncidentsWrapper {...mockIncidentWrapperProps} isOpen={true} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByText('There are 2 Incidents in Instance 1')
    ).toBeInTheDocument();
  });

  it('should render the table', () => {
    render(<IncidentsWrapper {...mockIncidentWrapperProps} isOpen={true} />, {
      wrapper: Wrapper,
    });
    userEvent.click(screen.getByTitle('View 2 Incidents in Instance 1'));

    const table = within(screen.getByTestId('incidents-table'));

    expect(table.getByText(/^Incident Type/)).toBeInTheDocument();
    expect(table.getByText(/^Flow Node/)).toBeInTheDocument();
    expect(table.getByText(/^Job Id/)).toBeInTheDocument();
    expect(table.getByText(/^Creation Time/)).toBeInTheDocument();
    expect(table.getByText(/^Error Message/)).toBeInTheDocument();
    expect(table.getByText(/^Operations/)).toBeInTheDocument();
  });

  it('should render the filters', () => {
    render(<IncidentsWrapper {...mockIncidentWrapperProps} isOpen={true} />, {
      wrapper: Wrapper,
    });
    userEvent.click(screen.getByTitle('View 2 Incidents in Instance 1'));

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
    let rerender: any;
    beforeEach(() => {
      const wrapper = render(
        <IncidentsWrapper {...mockIncidentWrapperProps} isOpen={true} />,
        {
          wrapper: Wrapper,
        }
      );
      rerender = wrapper.rerender;
      userEvent.click(screen.getByTitle('View 2 Incidents in Instance 1'));
    });

    it('should not have active filters by default', () => {
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    });

    it('should filter the incidents when errorTypes are selected', () => {
      const table = within(screen.getByTestId('incidents-table'));

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
      expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
      expect(table.getByText(/Extract value errortype/)).toBeInTheDocument();

      userEvent.click(
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

    it('should filter the incidents when flowNodes are selected', () => {
      const table = within(screen.getByTestId('incidents-table'));

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
      expect(
        table.getByText(/flowNodeId_exclusiveGateway/)
      ).toBeInTheDocument();
      expect(
        table.getByText(/flowNodeId_alwaysFailingTask/)
      ).toBeInTheDocument();

      userEvent.click(
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

    it('should filter the incidents when both errorTypes & flowNodes are selected', () => {
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
      userEvent.click(
        within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
          /^Condition errortype/
        )
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);

      const flowNodeFilters = within(
        screen.getByTestId(/incidents-by-flownode/i)
      );

      userEvent.click(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      );
      expect(screen.queryAllByLabelText(/^incident/i)).toHaveLength(0);
      userEvent.click(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
    });

    it('should remove filter when only related incident gets resolved', async () => {
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

      const flowNodeFilters = within(
        screen.getByTestId(/incidents-by-flownode/i)
      );

      userEvent.click(
        flowNodeFilters.getByText(/^flowNodeId_exclusiveGateway/)
      );
      userEvent.click(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

      // incident is resolved
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/incidents',
          (_, res, ctx) =>
            res.once(
              ctx.json(
                IS_NEXT_INCIDENTS
                  ? mockResolvedIncidents
                  : mockResolvedIncidentsLegacy
              )
            )
        )
      );

      await incidentsStore.fetchIncidents('1');

      rerender(
        <IncidentsWrapper {...mockIncidentWrapperProps} isOpen={true} />
      );

      expect(
        flowNodeFilters.queryByText(/^flowNodeId_exclusiveGateway/)
      ).not.toBeInTheDocument();
      expect(
        flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
      ).toBeInTheDocument();
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
    });

    it('should drop all filters when clicking the clear all button', () => {
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

      userEvent.click(
        within(screen.getByTestId(/incidents-by-flownode/i)).getByText(
          /^flowNodeId_exclusiveGateway/
        )
      );
      userEvent.click(
        within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
          /^Condition errortype/
        )
      );
      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);

      userEvent.click(screen.getByText(/^Clear All/));

      expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    });
  });
});
