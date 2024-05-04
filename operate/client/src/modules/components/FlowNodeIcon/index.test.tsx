/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
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
    );

    expect(
      screen.getByText('flow-node-task-undefined.svg'),
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
    );

    expect(
      screen.getByText('flow-node-multi-instance-sequential.svg'),
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
    );

    expect(
      screen.getByText('flow-node-multi-instance-parallel.svg'),
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
    );

    expect(
      screen.getByText('flow-node-event-timer-interrupting.svg'),
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
    );

    expect(
      screen.getByText('flow-node-event-message-non-interrupting.svg'),
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
    );

    expect(
      screen.getByText('flow-node-subprocess-event.svg'),
    ).toBeInTheDocument();
  });

  it('should render compensation end event', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType=""
        diagramBusinessObject={{
          id: 'compensationEndEvent',
          name: 'Compensation End Event',
          $type: 'bpmn:EndEvent',
          eventDefinitions: [
            {
              $type: 'bpmn:CompensateEventDefinition',
            },
          ],
        }}
      />,
    );

    expect(
      screen.getByText('flow-node-compensation-end-event.svg'),
    ).toBeInTheDocument();
  });
});
