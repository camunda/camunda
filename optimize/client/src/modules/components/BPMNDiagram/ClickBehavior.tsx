/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import Viewer from 'bpmn-js/lib/Viewer';

import {Canvas, ModdleElement} from './BPMNDiagram';

import './ClickBehavior.scss';

export interface RegistryElement extends ModdleElement, Iterable<RegistryElement> {
  businessObject: RegistryElement;
  get: (selector: string | ModdleElement) => RegistryElement;
  $instanceOf: (instanceName: string) => boolean;
  forEach: (cb: (element: RegistryElement, gfx: HTMLElement) => void) => void;
}

type RegistryEvent = Event & {element: RegistryElement};

interface ClickBehaviorProps {
  setSelectedNodes?: (nodes: ModdleElement[]) => void;
  nodeTypes?: string[];
  viewer?: Viewer;
  selectedNodes?: (string | ModdleElement)[];
  onClick: (e: ModdleElement) => void;
  onHover?: (e: ModdleElement | null) => void;
}

export default class ClickBehavior extends Component<ClickBehaviorProps> {
  render() {
    return null;
  }

  componentDidMount() {
    if (this.props.setSelectedNodes) {
      this.getNodeObjects();
    }
    this.setupEventListeners();
    this.update();
    this.roundEdges();
  }

  componentWillUnmount() {
    this.teardownEventListeners();
    this.removeMarkers();
  }

  componentDidUpdate() {
    this.update();
  }

  removeMarkers = () => {
    const {viewer} = this.props;
    const elementRegistry = viewer?.get<RegistryElement>('elementRegistry');
    const canvas = viewer?.get<Canvas>('canvas');

    // remove existing selection markers
    elementRegistry?.forEach((element) => {
      if (this.isValid(element)) {
        canvas?.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas?.removeMarker(element.businessObject.id, 'ClickBehavior__node');
      } else if (this.isDisabled(element.businessObject)) {
        canvas?.removeMarker(element.businessObject.id, 'ClickBehavior__disabled');
      }
    });
  };

  isDisabled = (element: RegistryElement['businessObject']) =>
    element.$instanceOf('bpmn:FlowNode') ||
    element.$instanceOf('bpmn:SequenceFlow') ||
    element.$instanceOf('bpmn:Lane') ||
    element.$instanceOf('bpmn:Participant');

  update() {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer?.get<RegistryElement>('elementRegistry');
    const canvas = viewer?.get<Canvas>('canvas');

    // remove existing selection markers and indicate selectable status for all flownodes
    elementRegistry?.forEach((element) => {
      if (this.isValid(element)) {
        canvas?.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas?.addMarker(element.businessObject.id, 'ClickBehavior__node');
      } else if (this.isDisabled(element.businessObject)) {
        canvas?.addMarker(element.businessObject.id, 'ClickBehavior__disabled');
      }
    });

    // add selection marker for all selected nodes
    selectedNodes?.forEach((elementId) => {
      canvas?.addMarker(elementId, 'ClickBehavior__node--selected');
    });
  }

  getNodeObjects = () => {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer?.get<RegistryElement>('elementRegistry');
    const nodes = selectedNodes
      ?.map((v) => elementRegistry?.get(v).businessObject)
      .filter((v): v is RegistryElement => !!v);
    this.props.setSelectedNodes?.(nodes || []);
  };

  roundEdges = () => {
    this.props.viewer?.get<RegistryElement>('elementRegistry').forEach((_element, gfx) => {
      const outline = gfx.querySelector('.djs-outline');
      if (outline) {
        outline.setAttribute('rx', '14px');
        outline.setAttribute('ry', '14px');
      }
    });
  };

  isValid = (element: RegistryElement) => {
    const {nodeTypes = ['FlowNode']} = this.props;
    return nodeTypes.some((type) => element.businessObject.$instanceOf('bpmn:' + type));
  };

  onClick = ({element}: RegistryEvent) => {
    if (this.isValid(element)) {
      this.props.onClick(element.businessObject);
    }
  };

  onHover = ({element}: RegistryEvent) => {
    if (this.props.onHover && this.isValid(element)) {
      this.props.onHover(element.businessObject);
    }
  };

  outHandler = ({element}: RegistryEvent) => {
    if (this.props.onHover && this.isValid(element)) {
      this.props.onHover(null);
    }
  };

  setupEventListeners() {
    this.props.viewer?.on('element.click', this.onClick);
    this.props.viewer?.on('element.hover', this.onHover);
    this.props.viewer?.on('element.out', this.outHandler);
  }

  teardownEventListeners = () => {
    this.props.viewer?.off('element.click', this.onClick);
    this.props.viewer?.off('element.hover', this.onHover);
    this.props.viewer?.off('element.out', this.outHandler);
  };
}
