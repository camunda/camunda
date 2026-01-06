/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ElementInstanceIcon} from './index';

describe('ElementInstanceIcon', () => {
  it('should render default icon', () => {
    render(
      <ElementInstanceIcon
        diagramBusinessObject={{
          id: 'unknown',
          name: 'Unknown',
          $type: 'bpmn:SequenceFlow',
        }}
      />,
    );

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render default icon for deleted element', () => {
    render(<ElementInstanceIcon diagramBusinessObject={undefined} />);

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render parallel multi instance body', () => {
    render(
      <ElementInstanceIcon
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

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render sequential multi instance body', () => {
    render(
      <ElementInstanceIcon
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

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render intermediate timer event', () => {
    render(
      <ElementInstanceIcon
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

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render message boundary event', () => {
    render(
      <ElementInstanceIcon
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

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render event sub process', () => {
    render(
      <ElementInstanceIcon
        diagramBusinessObject={{
          id: 'eventSubProcess1',
          name: 'Event Sub Process 1',
          $type: 'bpmn:SubProcess',
          triggeredByEvent: true,
        }}
      />,
    );

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });

  it('should render compensation end event', () => {
    render(
      <ElementInstanceIcon
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

    expect(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
  });
});
