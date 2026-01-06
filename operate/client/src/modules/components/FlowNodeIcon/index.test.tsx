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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
  });

  it('should render default icon for deleted flow node', () => {
    render(
      <FlowNodeIcon
        flowNodeInstanceType=""
        diagramBusinessObject={undefined}
      />,
    );

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
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

    expect(screen.getByTestId('flow-node-icon')).toBeInTheDocument();
  });
});
