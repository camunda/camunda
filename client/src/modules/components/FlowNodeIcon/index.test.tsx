/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {FlowNodeIcon} from '.';

describe('FlowNodeIcon', () => {
  it('should render default icon', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType=""
        diagramBusinessObject={{
          id: 'unknown',
          name: 'Unknown',
          $type: 'bpmn:SequenceFlow',
        }}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-task-undefined.svg')
    ).toBeInTheDocument();
  });

  it('should render parallel multi instance body', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType="MULTI_INSTANCE_BODY"
        diagramBusinessObject={{
          id: 'subProcess',
          name: 'Sub Process',
          $type: 'bpmn:SubProcess',
          loopCharacteristics: {
            $type: 'bpmn:MultiInstanceLoopCharacteristics',
            isSequential: false,
          },
        }}
      />,

      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-multi-instance-sequential.svg')
    ).toBeInTheDocument();
  });

  it('should render sequential multi instance body', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType="MULTI_INSTANCE_BODY"
        diagramBusinessObject={{
          id: 'subProcess',
          name: 'Sub Process',
          $type: 'bpmn:SubProcess',
          loopCharacteristics: {
            $type: 'bpmn:MultiInstanceLoopCharacteristics',
            isSequential: true,
          },
        }}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-multi-instance-parallel.svg')
    ).toBeInTheDocument();
  });

  it('should render intermediate timer event', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType=""
        diagramBusinessObject={{
          id: 'event1',
          name: 'Event',
          $type: 'bpmn:IntermediateCatchEvent',
          eventDefinitions: [
            {
              $type: 'bpmn:TimerEventDefinition',
            },
          ],
        }}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-event-timer-interrupting.svg')
    ).toBeInTheDocument();
  });

  it('should render message boundary event', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType=""
        diagramBusinessObject={{
          id: 'event1',
          name: 'Event',
          $type: 'bpmn:BoundaryEvent',
          eventDefinitions: [
            {
              $type: 'bpmn:MessageEventDefinition',
            },
          ],
          cancelActivity: false,
        }}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-event-message-non-interrupting.svg')
    ).toBeInTheDocument();
  });

  it('should render event sub process', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType=""
        diagramBusinessObject={{
          id: 'eventSubProcess1',
          name: 'Event Sub Process 1',
          $type: 'bpmn:SubProcess',
          triggeredByEvent: true,
        }}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-subprocess-event.svg')
    ).toBeInTheDocument();
  });
});
