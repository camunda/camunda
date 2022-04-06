/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {useEffect, useState} from 'react';
import {MemoryRouter} from 'react-router-dom';
import styled from 'styled-components';
import {rest} from 'msw';
import {Story} from '@storybook/react';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {instance} from 'modules/mocks/instance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import DiagramComponent from '.';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledInstanceMetadata,
  calledUnevaluatedDecisionMetadata,
  multiInstanceMetadata,
  multiInstancesMetadata,
  singleInstanceMetadata,
} from 'modules/mocks/metadata';

export default {
  title: 'Components/Diagram',
};

const Container = styled.div`
  display: flex;
  height: 500px;
  border: 1px solid #ccc;
`;

const Diagram = ({flowNodeId}: {flowNodeId: string}) => {
  const [diagram, setDiagram] = useState<any>(null);

  useEffect(() => {
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.setProcessInstance(instance);
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    async function init() {
      setDiagram(await parseDiagramXML(metadataDemoProcess));
      flowNodeSelectionStore.selectFlowNode({flowNodeId: flowNodeId});
    }
    init();

    return () => {
      processInstanceDetailsDiagramStore.reset();
      processInstanceDetailsStore.reset();
      flowNodeMetaDataStore.reset();
      flowNodeSelectionStore.reset();
    };
  }, [setDiagram, flowNodeId]);

  return (
    <ThemeProvider>
      <MemoryRouter>
        {diagram !== null && (
          <Container>
            <DiagramComponent
              selectableFlowNodes={[flowNodeId]}
              definitions={diagram.definitions}
              hidePopover={false}
              selectedFlowNodeId={flowNodeId}
            />
          </Container>
        )}
      </MemoryRouter>
    </ThemeProvider>
  );
};

const SingleInstance: Story = () => {
  return <Diagram flowNodeId="StartEvent" />;
};
SingleInstance.storyName = 'Metadata - Single Instance Flow Node';
SingleInstance.parameters = {
  msw: [
    rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
      return res(ctx.text(metadataDemoProcess));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/flow-node-metadata',
      (_, res, ctx) => {
        return res(ctx.json(singleInstanceMetadata));
      }
    ),
  ],
};

const CalledInstance: Story = () => <Diagram flowNodeId="CallActivity" />;
CalledInstance.storyName = 'Metadata - Single Incident With Called Instance';
CalledInstance.parameters = {
  msw: [
    rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
      return res(ctx.text(metadataDemoProcess));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/flow-node-metadata',
      (_, res, ctx) => {
        return res(ctx.json(calledInstanceMetadata));
      }
    ),
  ],
};

const MultipleInstances: Story = () => <Diagram flowNodeId="Task" />;
MultipleInstances.storyName = 'Metadata - Multiple Flow Node Instances';
MultipleInstances.parameters = {
  msw: [
    rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
      return res(ctx.text(metadataDemoProcess));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/flow-node-metadata',
      (_, res, ctx) => {
        return res(ctx.json(multiInstancesMetadata));
      }
    ),
  ],
};

const MultiInstance: Story = () => <Diagram flowNodeId="Task" />;
MultiInstance.storyName = 'Metadata - Multi Instance Flow Node';
MultiInstance.parameters = {
  msw: [
    rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
      return res(ctx.text(metadataDemoProcess));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/flow-node-metadata',
      (_, res, ctx) => {
        return res(ctx.json(multiInstanceMetadata));
      }
    ),
  ],
};

const CalledEvaluatedDecision: Story = () => (
  <Diagram flowNodeId="BusinessRuleTask" />
);
CalledEvaluatedDecision.storyName = 'Metadata - Called Evaluated Decision';
CalledEvaluatedDecision.parameters = {
  msw: {
    handlers: [
      rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
        return res(ctx.text(metadataDemoProcess));
      }),
      rest.post(
        '/api/process-instances/:processInstanceId/flow-node-metadata',
        (_, res, ctx) => {
          return res(ctx.json(calledDecisionMetadata));
        }
      ),
    ],
  },
};

const CalledFailedDecision: Story = () => (
  <Diagram flowNodeId="BusinessRuleTask" />
);
CalledFailedDecision.storyName = 'Metadata - Called Failed Decision';
CalledFailedDecision.parameters = {
  msw: [
    rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
      return res(ctx.text(metadataDemoProcess));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/flow-node-metadata',
      (_, res, ctx) => {
        return res(ctx.json(calledFailedDecisionMetadata));
      }
    ),
  ],
};

const CalledUnevaluatedDecision: Story = () => (
  <Diagram flowNodeId="BusinessRuleTask" />
);
CalledUnevaluatedDecision.storyName = 'Metadata - Called Unevaluated Decision';
CalledUnevaluatedDecision.parameters = {
  msw: [
    rest.get('/api/processes/:processInstanceId/xml', (_, res, ctx) => {
      return res(ctx.text(metadataDemoProcess));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/flow-node-metadata',
      (_, res, ctx) => {
        return res(ctx.json(calledUnevaluatedDecisionMetadata));
      }
    ),
  ],
};

export {
  SingleInstance,
  MultipleInstances,
  MultiInstance,
  CalledInstance,
  CalledEvaluatedDecision,
  CalledUnevaluatedDecision,
  CalledFailedDecision,
};
