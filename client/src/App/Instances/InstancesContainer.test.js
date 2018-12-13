import React from 'react';
import {mount} from 'enzyme';
import {BrowserRouter as Router} from 'react-router-dom';

import InstancesContainer from './InstancesContainer';
import Instances from './Instances';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {formatDiagramNodes, parseQueryString} from './service';
import * as api from 'modules/api/instances/instances';
import * as apiDiagram from 'modules/api/diagram/diagram';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {getFilterQueryString} from 'modules/utils/filter';
import {DEFAULT_FILTER} from 'modules/constants';
import {getDiagramNodes} from 'modules/testUtils';
const InstancesContainerWrapped = InstancesContainer.WrappedComponent;

// component mock
jest.mock(
  './Instances',
  () =>
    function InstancesMock(props) {
      return <div />;
    }
);
// what to do with this
const groupedWorkflowsMock = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
];

const fullFilterWithoutWorkflow = {
  active: true,
  incidents: true,
  completed: true,
  finished: true,
  ids: '424242, 434343',
  errorMessage: 'loremIpsum',
  startDate: '28 December 2018',
  endDate: '28 December 2018'
};

const fullFilterWithWorkflow = {
  active: true,
  incidents: true,
  completed: true,
  finished: true,
  ids: '424242, 434343',
  errorMessage: 'loremIpsum',
  startDate: '28 December 2018',
  endDate: '28 December 2018',
  workflow: 'demoProcess',
  version: 1,
  activityId: 'taskD'
};

const localStorageProps = {
  getStateLocally: jest.fn(),
  storeStateLocally: jest.fn()
};
const pushMock = jest.fn();
function getRouterProps(filter = DEFAULT_FILTER) {
  return {
    history: {push: pushMock},
    location: {
      search: getFilterQueryString(filter)
    }
  };
}

// api mocks
api.fetchGroupedWorkflowInstances = mockResolvedAsyncFn(groupedWorkflowsMock);
apiDiagram.fetchWorkflowXML = mockResolvedAsyncFn('<xml />');
jest.mock('bpmn-js', () => ({}));
jest.mock('modules/utils/bpmn');

describe('InstancesContainer', () => {
  afterEach(() => {
    pushMock.mockClear();
  });

  it('should fetch the groupedWorkflowInstances', async () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainerWrapped
              {...localStorageProps}
              {...getRouterProps()}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );

    //when
    await flushPromises();
    node.update();

    expect(api.fetchGroupedWorkflowInstances).toHaveBeenCalled();
  });

  it('should write the filter to local storage', async () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainer {...getRouterProps()} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );

    //when
    await flushPromises();
    node.update();

    expect(localStorage.setItem).toHaveBeenCalled();
  });

  it('should render the Instances', () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainerWrapped
              {...localStorageProps}
              {...getRouterProps()}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );
    expect(node.find(Instances)).toExist();
  });

  it('should pass data to Instances for default filter', async () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainerWrapped
              {...localStorageProps}
              {...getRouterProps()}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );

    //when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);
    const InstancesContainerNode = node.find(InstancesContainerWrapped);
    expect(
      InstancesNode.prop('groupedWorkflowInstances')[
        groupedWorkflowsMock[0].bpmnProcessId
      ]
    ).not.toBe(undefined);
    expect(
      InstancesNode.prop('groupedWorkflowInstances')[
        groupedWorkflowsMock[1].bpmnProcessId
      ]
    ).not.toBe(undefined);
    expect(InstancesNode.prop('filter')).toEqual(DEFAULT_FILTER);
    expect(InstancesNode.prop('diagramWorkflow')).toBe(
      InstancesContainerNode.state().currentWorkflow
    );
    expect(InstancesNode.prop('onFilterChange')).toBe(
      InstancesContainerNode.instance().setFilterInURL
    );
    expect(InstancesNode.prop('diagramNodes')).toEqual([]);
  });
  it('should pass data to Instances for full filter, without workflow data', async () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainerWrapped
              {...localStorageProps}
              {...getRouterProps(fullFilterWithoutWorkflow)}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );

    //when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(fullFilterWithoutWorkflow);
    expect(InstancesNode.prop('diagramWorkflow')).toEqual({});
    expect(InstancesNode.prop('diagramNodes')).toEqual([]);
  });
  it('should pass data to Instances for full filter, with workflow data', async () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainerWrapped
              {...localStorageProps}
              {...getRouterProps(fullFilterWithWorkflow)}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );

    //when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(fullFilterWithWorkflow);
    expect(InstancesNode.prop('diagramWorkflow')).toEqual(
      groupedWorkflowsMock[0].workflows[2]
    );
    expect(InstancesNode.prop('diagramNodes')).toEqual(
      formatDiagramNodes(getDiagramNodes())
    );
  });
  it('should pass data to Instances for full filter, with all versions', async () => {
    const {activityId, version, ...rest} = fullFilterWithWorkflow;
    const node = mount(
      <Router>
        <ThemeProvider>
          <CollapsablePanelProvider>
            <InstancesContainerWrapped
              {...localStorageProps}
              {...getRouterProps({
                ...rest,
                version: 'all'
              })}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );

    //when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);
    expect(InstancesNode.prop('filter')).toEqual({
      ...rest,
      version: 'all'
    });
    expect(InstancesNode.prop('diagramWorkflow')).toEqual({});
    expect(InstancesNode.prop('diagramNodes')).toEqual([]);
  });

  describe('should fix an invalid filter in url', () => {
    it('should add the default filter to the url when no filter is present', async () => {
      const noFilterRouterProps = {
        history: {push: jest.fn()},
        location: {
          search: ''
        }
      };

      mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <InstancesContainerWrapped
                {...localStorageProps}
                {...noFilterRouterProps}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      await flushPromises();

      expect(noFilterRouterProps.history.push).toHaveBeenCalled();
      expect(noFilterRouterProps.history.push.mock.calls[0][0].search).toBe(
        '?filter={"active":true,"incidents":true}'
      );
    });

    it('when the filter in url is invalid', async () => {
      const invalidFilterRouterProps = {
        history: {push: jest.fn()},
        location: {
          search:
            '?filter={"active": fallse, "errorMessage": "No more retries left"'
        }
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <InstancesContainerWrapped
                {...localStorageProps}
                {...invalidFilterRouterProps}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      await flushPromises();
      node.update();

      expect(invalidFilterRouterProps.history.push).toHaveBeenCalled();
      expect(
        invalidFilterRouterProps.history.push.mock.calls[0][0].search
      ).toBe('?filter={"active":true,"incidents":true}');
    });

    it('when the workflow in url is invalid', async () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <InstancesContainerWrapped
                {...localStorageProps}
                {...getRouterProps({
                  ...fullFilterWithWorkflow,
                  workflow: 'x'
                })}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      await flushPromises();
      node.update();

      // expect invalid activityId to have been removed
      expect(pushMock).toHaveBeenCalled();
      const search = pushMock.mock.calls[0][0].search;
      const {version, workflow, activityId, ...rest} = fullFilterWithWorkflow;

      expect(parseQueryString(search).filter).toEqual(rest);
    });

    it('when the version in url is invalid', async () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <InstancesContainerWrapped
                {...localStorageProps}
                {...getRouterProps({
                  ...fullFilterWithWorkflow,
                  version: 'x'
                })}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      await flushPromises();
      node.update();

      // expect invalid activityId to have been removed
      expect(pushMock).toHaveBeenCalled();
      const search = pushMock.mock.calls[0][0].search;
      const {version, workflow, activityId, ...rest} = fullFilterWithWorkflow;

      expect(parseQueryString(search).filter).toEqual(rest);
    });

    it('when the activityId in url is invalid', async () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <InstancesContainerWrapped
                {...localStorageProps}
                {...getRouterProps({
                  ...fullFilterWithWorkflow,
                  activityId: 'x'
                })}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      await flushPromises();
      node.update();

      // expect invalid activityId to have been removed
      expect(pushMock).toHaveBeenCalledTimes(1);
      const search = pushMock.mock.calls[0][0].search;
      const {activityId, ...rest} = fullFilterWithWorkflow;
      expect(parseQueryString(search).filter).toEqual(rest);
    });

    it('should remove activityId when version="all"', async () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <InstancesContainerWrapped
                {...localStorageProps}
                {...getRouterProps({
                  ...fullFilterWithWorkflow,
                  version: 'all'
                })}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      await flushPromises();
      node.update();

      // expect invalid activityId to have been removed
      expect(pushMock).toHaveBeenCalledTimes(1);
      const search = pushMock.mock.calls[0][0].search;
      const {activityId, version, ...rest} = fullFilterWithWorkflow;
      expect(parseQueryString(search).filter).toEqual({
        ...rest,
        version: 'all'
      });
    });
  });
});
