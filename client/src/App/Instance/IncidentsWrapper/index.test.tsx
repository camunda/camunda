/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {IncidentsWrapper} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {testData, mockIncidents, mockResolvedIncidents} from './index.setup';
import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {incidentsStore} from 'modules/stores/incidents';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

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

describe('IncidentsWrapper', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      )
    );

    await incidentsStore.fetchIncidents('1');
  });

  it('should render the IncidentsBanner', () => {
    render(<IncidentsWrapper {...testData.props.default} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByText('There are 2 Incidents in Instance 1')
    ).toBeInTheDocument();
  });

  it('should toggle the IncidentsOverlay when clicking on the IncidentsBanner', () => {
    render(<IncidentsWrapper {...testData.props.default} />, {
      wrapper: Wrapper,
    });
    expect(screen.queryByText('Incident Type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();
    fireEvent.click(
      screen.getByRole('button', {
        name: 'View 2 Incidents in Instance 1',
      })
    );
    expect(screen.getByText('Incident type:')).toBeInTheDocument();
    expect(screen.getByText('Flow Node:')).toBeInTheDocument();
  });

  it('should render the IncidentsTable', () => {
    render(<IncidentsWrapper {...testData.props.default} />, {
      wrapper: Wrapper,
    });
    fireEvent.click(
      screen.getByRole('button', {
        name: 'View 2 Incidents in Instance 1',
      })
    );

    expect(
      screen.getByRole('columnheader', {name: /^Incident Type/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^Flow Node/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^Job Id/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^Creation Time/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^Error Message/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^Operations/})
    ).toBeInTheDocument();
  });

  it('should render the IncidentsFilter', () => {
    render(<IncidentsWrapper {...testData.props.default} />, {
      wrapper: Wrapper,
    });
    fireEvent.click(
      screen.getByRole('button', {
        name: 'View 2 Incidents in Instance 1',
      })
    );

    expect(
      screen.getByRole('button', {name: /^Condition error/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /^Extract value error/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /^flowNodeId_exclusiveGateway/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /^flowNodeId_alwaysFailingTask/})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /^Clear All/})
    ).toBeInTheDocument();
  });

  describe('Filtering', () => {
    let rerender: any;
    beforeEach(() => {
      const wrapper = render(<IncidentsWrapper {...testData.props.default} />, {
        wrapper: Wrapper,
      });
      rerender = wrapper.rerender;
      fireEvent.click(
        screen.getByRole('button', {
          name: 'View 2 Incidents in Instance 1',
        })
      );
    });

    it('should not have active filters by default', () => {
      expect(screen.getAllByRole('row').length).toBe(3);
    });

    it('should filter the incidents when errorTypes are selected', () => {
      expect(screen.getAllByRole('row').length).toBe(3);
      expect(
        screen.getByRole('row', {name: /Condition errortype/})
      ).toBeInTheDocument();
      expect(
        screen.getByRole('row', {name: /Extract value errortype/})
      ).toBeInTheDocument();

      fireEvent.click(
        screen.getByRole('button', {name: /^Condition errortype/})
      );

      expect(screen.getAllByRole('row').length).toBe(2);
      expect(
        screen.getByRole('row', {name: /Condition errortype/})
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('row', {name: /Extract value errortype/})
      ).not.toBeInTheDocument();
    });

    it('should filter the incidents when flowNodes are selected', () => {
      expect(screen.getAllByRole('row').length).toBe(3);
      expect(
        screen.getByRole('row', {name: /flowNodeId_exclusiveGateway/})
      ).toBeInTheDocument();
      expect(
        screen.getByRole('row', {name: /flowNodeId_alwaysFailingTask/})
      ).toBeInTheDocument();

      fireEvent.click(
        screen.getByRole('button', {name: /^flowNodeId_exclusiveGateway/})
      );

      expect(screen.getAllByRole('row').length).toBe(2);
      expect(
        screen.getByRole('row', {name: /flowNodeId_exclusiveGateway/})
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('row', {name: /flowNodeId_alwaysFailingTask/})
      ).not.toBeInTheDocument();
    });

    it('should filter the incidents when both errorTypes & flowNodes are selected', () => {
      expect(screen.getAllByRole('row').length).toBe(3);
      fireEvent.click(
        screen.getByRole('button', {name: /^Condition errortype/})
      );
      expect(screen.getAllByRole('row').length).toBe(2);

      fireEvent.click(
        screen.getByRole('button', {name: /^flowNodeId_alwaysFailingTask/})
      );
      expect(screen.getAllByRole('row').length).toBe(1);
      fireEvent.click(
        screen.getByRole('button', {name: /^flowNodeId_alwaysFailingTask/})
      );
      expect(screen.getAllByRole('row').length).toBe(2);
    });

    it('should remove filter when only related incident gets resolved', async () => {
      expect(screen.getAllByRole('row').length).toBe(3);

      fireEvent.click(
        screen.getByRole('button', {name: /^flowNodeId_exclusiveGateway/})
      );
      fireEvent.click(
        screen.getByRole('button', {name: /^flowNodeId_alwaysFailingTask/})
      );
      expect(screen.getAllByRole('row').length).toBe(3);

      // incident is resolved
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/incidents',
          (_, res, ctx) => res.once(ctx.json(mockResolvedIncidents))
        )
      );

      await incidentsStore.fetchIncidents('1');

      rerender(<IncidentsWrapper {...testData.props.default} />);

      expect(
        screen.queryByRole('button', {name: /^flowNodeId_exclusiveGateway/})
      ).not.toBeInTheDocument();

      expect(
        screen.getByRole('button', {name: /^flowNodeId_alwaysFailingTask/})
      ).toBeInTheDocument();

      expect(screen.getAllByRole('row').length).toBe(2);
    });

    it('should drop all filters when clicking the clear all button', () => {
      expect(screen.getAllByRole('row').length).toBe(3);

      fireEvent.click(
        screen.getByRole('button', {name: /^flowNodeId_exclusiveGateway/})
      );
      fireEvent.click(
        screen.getByRole('button', {name: /^Condition errortype/})
      );
      expect(screen.getAllByRole('row').length).toBe(2);

      fireEvent.click(screen.getByRole('button', {name: /^Clear All/}));

      expect(screen.getAllByRole('row').length).toBe(3);
    });
  });
});
