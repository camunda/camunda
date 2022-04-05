/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Story} from '@storybook/react';
import {Instance as InstanceDetail} from './index';
import {rest} from 'msw';
import {
  xml,
  flowNodeInstances,
  flowNodeStates,
  instance,
  sequenceFlows,
  callHierarchy,
  longCallHierarchy,
} from 'modules/mocks/instanceDetailPage/runningInstance';

import {
  xml as incidentXml,
  flowNodeInstances as incidentFlowNodeInstances,
  flowNodeStates as incidentFlowNodeStates,
  instance as incidentInstance,
  sequenceFlows as incidentSequenceFlows,
  incidents,
  variables as incidentVariables,
} from 'modules/mocks/instanceDetailPage/instanceWithAnIncident';

import {
  xml as completedInstanceXml,
  flowNodeInstances as completedInstanceFlowNodeInstances,
  flowNodeStates as completedInstanceFlowNodeStates,
  instance as completedInstance,
  sequenceFlows as completedInstanceSequenceFlows,
  variables as completedInstanceVariables,
} from 'modules/mocks/instanceDetailPage/completedInstance';

import {
  xml as canceledInstanceXml,
  flowNodeInstances as canceledInstanceFlowNodeInstances,
  flowNodeStates as canceledInstanceFlowNodeStates,
  instance as canceledInstance,
  sequenceFlows as canceledInstanceSequenceFlows,
} from 'modules/mocks/instanceDetailPage/canceledInstance';

import {
  xml as miXml,
  flowNodeInstances as miFlowNodeInstances,
  flowNodeStates as miFlowNodeStates,
  instance as miInstance,
  sequenceFlows as miSequenceFlows,
  incidents as miIncidents,
  flowNodeMetadata,
} from 'modules/mocks/instanceDetailPage/processWithMultiInstance';
import {statistics} from 'modules/mocks/statistics';
import {user} from 'modules/mocks/user';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useEffect} from 'react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {Layout} from 'App/Layout';

const mocks = [
  rest.get('/api/authentications/user', (_, res, ctx) => {
    return res(ctx.json(user));
  }),
  rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
    return res(ctx.json(statistics));
  }),
];

export default {
  title: 'Pages/Instance Detail',
};

const RunningInstance: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/2251799813685591']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

RunningInstance.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json(instance))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(sequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(flowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(flowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) => res(ctx.text(xml))),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json([]))
    ),
  ],
};

const Error: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/2251799813685591']}>
      <Routes>
        <Route path="instances/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Error.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json(instance))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.status(500), ctx.json({}))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.status(500), ctx.json({}))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.status(500), ctx.json({}))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) =>
      res(ctx.status(500), ctx.text(''))
    ),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.status(500), ctx.json([]))
    ),
  ],
};

const Skeleton: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/2251799813685591']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Skeleton.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.delay('infinite'), ctx.json(instance))
    ),
  ],
};

const Incident: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/6755399441057842']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Incident.storyName = 'Instance with an incident';
Incident.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json(incidentInstance))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(incidentSequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(incidentFlowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(incidentFlowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) =>
      res(ctx.text(incidentXml))
    ),
    rest.get('/api/process-instances/:id/incidents', (_, res, ctx) =>
      res(ctx.json(incidents))
    ),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json(incidentVariables))
    ),
  ],
};

const CompletedInstance: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/9007199254741571']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

CompletedInstance.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json(completedInstance))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(completedInstanceSequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(completedInstanceFlowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(completedInstanceFlowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) =>
      res(ctx.text(completedInstanceXml))
    ),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json(completedInstanceVariables))
    ),
  ],
};

const CanceledInstance: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/4503599627371108']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

CanceledInstance.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json(canceledInstance))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(canceledInstanceSequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(canceledInstanceFlowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(canceledInstanceFlowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) =>
      res(ctx.text(canceledInstanceXml))
    ),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json([]))
    ),
  ],
};

const MultiInstanceSelected: Story = () => {
  useEffect(() => {
    async function selectFlowNode() {
      await currentInstanceStore.fetchCurrentInstance('2251799813686430');

      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'taskB',
        isMultiInstance: true,
      });
    }

    selectFlowNode();
  }, []);

  return (
    <MemoryRouter initialEntries={['/processes/2251799813686430']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

MultiInstanceSelected.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json(miInstance))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(miSequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(miFlowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(miFlowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) => res(ctx.text(miXml))),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json([]))
    ),
    rest.get('/api/process-instances/:id/incidents', (_, res, ctx) =>
      res(ctx.json(miIncidents))
    ),
    rest.post('/api/process-instances/:id/flow-node-metadata', (_, res, ctx) =>
      res(ctx.json(flowNodeMetadata))
    ),
  ],
};

const ChildInstance: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/2251799813685591']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

ChildInstance.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json({...instance, callHierarchy}))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(sequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(flowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(flowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) => res(ctx.text(xml))),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json([]))
    ),
  ],
};

const ChildInstanceWithLongParentHierarchy: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes/2251799813685591']}>
      <Routes>
        <Route path="processes/:processInstanceId" element={<Layout />}>
          <Route index element={<InstanceDetail />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

ChildInstanceWithLongParentHierarchy.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/:id', (_, res, ctx) =>
      res(ctx.json({...instance, callHierarchy: longCallHierarchy}))
    ),
    rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
      res(ctx.json(sequenceFlows))
    ),
    rest.get('/api/process-instances/:id/flow-node-states', (_, res, ctx) =>
      res(ctx.json(flowNodeStates))
    ),
    rest.post('/api/flow-node-instances', (_, res, ctx) =>
      res(ctx.json(flowNodeInstances))
    ),
    rest.get('/api/processes/:id/xml', (_, res, ctx) => res(ctx.text(xml))),
    rest.post('/api/process-instances/:id/variables', (_, res, ctx) =>
      res(ctx.json([]))
    ),
  ],
};

export {
  RunningInstance,
  Error,
  Skeleton,
  Incident,
  CompletedInstance,
  CanceledInstance,
  MultiInstanceSelected,
  ChildInstance,
  ChildInstanceWithLongParentHierarchy,
};
