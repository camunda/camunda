/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

declare module 'bpmn-js/lib/util/ModelUtil' {
  export function is(element: BpmnElement, type: string): boolean;
}

declare module 'bpmn-moddle' {
  import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

  export type DiagramModel = {
    elementsById: {
      [id: string]: BusinessObject;
    };
  };

  class BpmnModdle {
    constructor();
    fromXML(xml: BpmnElement, definitions: string): Promise<DiagramModel>;
  }

  export = BpmnModdle;
}

declare module 'bpmn-js/lib/NavigatedViewer' {
  export type EventType = `bpmn:${
    | 'MessageEventDefinition'
    | 'ErrorEventDefinition'
    | 'TimerEventDefinition'
    | 'TerminateEventDefinition'
    | 'LinkEventDefinition'
    | 'EscalationEventDefinition'
    | 'SignalEventDefinition'
    | 'CompensateEventDefinition'}`;

  export type FlowNodeType = `bpmn:${
    | 'StartEvent'
    | 'EndEvent'
    | 'IntermediateCatchEvent'
    | 'IntermediateThrowEvent'
    | 'EventBasedGateway'
    | 'ParallelGateway'
    | 'ExclusiveGateway'
    | 'InclusiveGateway'
    | 'SubProcess'
    | 'AdHocSubProcess'
    | 'ServiceTask'
    | 'UserTask'
    | 'BusinessRuleTask'
    | 'ScriptTask'
    | 'ReceiveTask'
    | 'SendTask'
    | 'ManualTask'
    | 'CallActivity'
    | 'BoundaryEvent'}`;

  export type ElementType =
    | FlowNodeType
    | 'label'
    | `bpmn:${'Process' | 'SequenceFlow' | 'Association'}`;

  export type BusinessObject = {
    id: string;
    name: string;
    $type: ElementType;
    isInterrupting?: boolean;
    $parent?: BusinessObject;
    sourceRef?: BusinessObject;
    incoming?: BusinessObject[];
    flowElements?: BusinessObject[];
    loopCharacteristics?: {$type: string; isSequential: boolean};
    extensionElements?: {
      values: {
        $type: string;
        $children?: {
          $type: string;
          source: string;
          target: string;
        }[];
      }[];
    };
    eventDefinitions?: {$type: EventType}[];
    cancelActivity?: boolean;
    triggeredByEvent?: boolean;
    $instanceOf?: (type: string) => boolean;
    isForCompensation?: boolean;
    targetRef?: BusinessObject;
  };

  export type BpmnElement = {
    id: string;
    type: ElementType;
    businessObject: BusinessObject;
    di: {set: Function};
    width: number;
    height: number;
  };

  export type Event = {
    element: BpmnElement;
    gfx: SVGElement;
  };

  export type EventCallback = (
    event: string,
    callback: (event: Event) => void,
  ) => void;

  export type OverlayPosition = {
    top?: number;
    right?: number;
    bottom?: number;
    left?: number;
  };

  declare class NavigatedViewer {
    constructor({
      container,
      bpmnRenderer,
      canvas,
      additionalModules,
    }: {
      container: HTMLElement;
      bpmnRenderer: {[moduleName]: unknown};
      canvas: {deferUpdate: boolean};
      additionalModules: unknown[];
    });

    importXML: (xml: string) => Promise<{warnings: string[]}>;
    destroy: () => void;
    get(module: 'elementRegistry'): {
      get(elementId: BpmnElement['id']): BpmnElement;
      filter(callback: (element: BpmnElement) => boolean): BpmnElement[];
      getGraphics(element: BpmnElement): SVGGraphicsBpmnElement;
      getGraphics(elementId: BpmnElement['id']): SVGGraphicsElement;
    };
    get(module: 'canvas'): {
      getRootElement(): BpmnElement;
      findRoot(elementId: BpmnElement['id']): BpmnElement | undefined;
      setRootElement(element: BpmnElement): void;
      removeMarker(elementId: BpmnElement['id'], className: string): void;
      addMarker(elementId: BpmnElement['id'], className: string): void;
      resized(): void;
      zoom(
        newScale: number | 'fit-viewport',
        center: 'auto' | {x: number; y: number} | null,
      ): void;
    };
    get(module: 'graphicsFactory'): {
      update(type: string, element: BpmnElement, gfx: SVGGraphicsElement): void;
    };
    get(module: 'overlays'): {
      add(
        elementId: BpmnElement['id'],
        type: string,
        overlay: {
          html: HTMLElement;
          position: OverlayPosition;
          scale?: {min: number; max: number};
        },
      );
      clear(): void;
      remove({element, type}: {element?: string; type?: string}): void;
    };
    get(module: 'zoomScroll'): {
      stepZoom(step: number): void;
    };

    on: EventCallback;
    off: EventCallback;
  }

  export = NavigatedViewer;
}
