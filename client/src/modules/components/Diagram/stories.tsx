/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import {currentInstanceStore} from 'modules/stores/currentInstance';
import DiagramComponent from '.';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

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
    singleInstanceDiagramStore.init();
    currentInstanceStore.setCurrentInstance(instance);
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    async function init() {
      setDiagram(await parseDiagramXML(metadataDemoProcess));
      flowNodeSelectionStore.selectFlowNode({flowNodeId: flowNodeId});
    }
    init();

    return () => {
      singleInstanceDiagramStore.reset();
      currentInstanceStore.reset();
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
              expandState={'DEFAULT'}
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
        return res(
          ctx.json({
            flowNodeInstanceId: '2251799813695582',
            flowNodeId: null,
            flowNodeType: null,
            instanceCount: null,
            breadcrumb: [],
            instanceMetadata: {
              flowNodeId: 'start',
              flowNodeInstanceId: '2251799813695582',
              flowNodeType: 'START_EVENT',
              startDate: '2021-08-04T10:13:02.374+0000',
              endDate: '2021-08-04T10:13:02.376+0000',
              calledProcessInstanceId: null,
              calledProcessDefinitionName: null,
              eventId: '2251799813695565_2251799813695582',
              jobType: null,
              jobRetries: null,
              jobWorker: null,
              jobDeadline: null,
              jobCustomHeaders: null,
              incidentErrorType: null,
              incidentErrorMessage: null,
              jobId: null,
            },
          })
        );
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
        return res(
          ctx.json({
            flowNodeInstanceId: '2251799813691726',
            flowNodeId: null,
            flowNodeType: null,
            instanceCount: null,
            breadcrumb: [],
            instanceMetadata: {
              flowNodeId: 'CallActivity',
              flowNodeInstanceId: '2251799813691726',
              flowNodeType: 'CALL_ACTIVITY',
              startDate: '2021-08-04T10:12:00.244+0000',
              endDate: null,
              calledProcessInstanceId: '2251799813673452',
              calledProcessDefinitionName: 'Called Process',
              eventId: '2251799813691692_2251799813691726',
              jobType: null,
              jobRetries: null,
              jobWorker: null,
              jobDeadline: null,
              jobCustomHeaders: null,
              incidentErrorType: 'CALLED_ELEMENT_ERROR',
              incidentErrorMessage:
                "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
              jobId: null,
            },
          })
        );
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
        return res(
          ctx.json({
            flowNodeInstanceId: null,
            flowNodeId: 'Task',
            flowNodeType: 'SERVICE_TASK',
            instanceCount: 3,
            breadcrumb: [],
            instanceMetadata: null,
          })
        );
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
        return res(
          ctx.json({
            flowNodeInstanceId: '2251799813695594',
            flowNodeId: null,
            flowNodeType: null,
            instanceCount: null,
            breadcrumb: [
              {
                flowNodeId: 'Task',
                flowNodeType: 'MULTI_INSTANCE_BODY',
              },
              {
                flowNodeId: 'Task',
                flowNodeType: 'SERVICE_TASK',
              },
            ],
            instanceMetadata: {
              flowNodeId: 'Task',
              flowNodeInstanceId: '2251799813695594',
              flowNodeType: 'SERVICE_TASK',
              startDate: '2021-08-04T10:13:02.413+0000',
              endDate: null,
              calledProcessInstanceId: null,
              calledProcessDefinitionName: null,
              eventId: '2251799813695565_2251799813695594',
              jobType: null,
              jobRetries: null,
              jobWorker: null,
              jobDeadline: null,
              jobCustomHeaders: null,
              incidentErrorType: null,
              incidentErrorMessage: null,
              jobId: null,
            },
          })
        );
      }
    ),
  ],
};

export {SingleInstance, MultipleInstances, MultiInstance, CalledInstance};
