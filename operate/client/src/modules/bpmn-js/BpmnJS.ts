/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import NavigatedViewer, {
  type BpmnElement,
  type Event,
  type OverlayPosition,
} from 'bpmn-js/lib/NavigatedViewer';
import OutlineModule from 'bpmn-js/lib/features/outline';
// @ts-expect-error Could not find a declaration file for module '@bpmn-io/element-templates-icons-renderer'
import ElementTemplatesIconsRenderer from '@bpmn-io/element-template-icon-renderer';
// @ts-expect-error Could not find a declaration file for module 'diagram-js-minimap'
import minimapModule from 'diagram-js-minimap';
import 'diagram-js-minimap/assets/diagram-js-minimap.css';
import isEqual from 'lodash/isEqual';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {isNonSelectableElement} from './utils/isNonSelectableElement';
import {isMultiInstance} from './utils/isMultiInstance';
import {tracking} from 'modules/tracking';
import {bpmnRendererColors, highlightedSequenceFlowsColor} from './styled';
import type {OverlayData} from './overlayTypes';

type OnElementSelection = (
  elementId?: string,
  isMultiInstance?: boolean,
) => void;

type OnElementDoubleClick = (elementId: string) => void;

type RenderOptions = {
  container: HTMLElement;
  xml: string;
  selectableElements?: string[];
  selectedElementIds?: string[];
  overlaysData?: OverlayData[];
  highlightedSequenceFlows?: string[];
  highlightedElementIds?: string[];
  nonSelectableNodeTooltipText?: string;
  hasOuterBorderOnSelection: boolean;
  customElementClasses?: [elementId: string, className: string][];
};

class BpmnJS {
  #navigatedViewer: NavigatedViewer | null = null;
  #xml: string | null = null;
  #selectableElements: string[] = [];
  #nonSelectableElements: string[] = [];
  #selectedElementIds?: string[];
  #highlightedSequenceFlows: string[] = [];
  #highlightedElementIds: string[] = [];
  selectedElement?: SVGGraphicsElement;
  onElementSelection?: OnElementSelection;
  onElementDoubleClick?: OnElementDoubleClick;
  onViewboxChange?: (isChanging: boolean) => void;
  onRootChange?: (rootElementId: string) => void;
  #overlaysData: OverlayData[] = [];
  #hasOuterBorderOnSelection = false;
  #rootElement?: BpmnElement;
  #customElementClasses: [elementId: string, className: string][] = [];

  import = async (xml: string) => {
    // Cleanup before importing
    this.#navigatedViewer!.off('element.click', this.#handleElementClick);
    this.#navigatedViewer!.off(
      'element.dblclick',
      this.#handleElementDoubleClick,
    );
    this.#navigatedViewer!.off('canvas.viewbox.changing', () => {
      this.onViewboxChange?.(true);
    });
    this.#navigatedViewer!.off('canvas.viewbox.changed', () => {
      this.onViewboxChange?.(false);
    });
    this.#navigatedViewer!.off('root.set', this.#handleRootChange);

    this.#overlaysData = [];
    this.#selectableElements = [];
    this.#selectedElementIds = undefined;
    this.#hasOuterBorderOnSelection = false;
    this.#customElementClasses = [];
    this.#rootElement = undefined;

    await this.#navigatedViewer!.importXML(xml);

    // Initialize after importing
    this.zoomReset();
    this.#navigatedViewer!.on('element.click', this.#handleElementClick);
    this.#navigatedViewer!.on(
      'element.dblclick',
      this.#handleElementDoubleClick,
    );
    this.#navigatedViewer!.on('canvas.viewbox.changing', () => {
      this.onViewboxChange?.(true);
    });
    this.#navigatedViewer!.on('canvas.viewbox.changed', () => {
      this.onViewboxChange?.(false);
    });
    this.#navigatedViewer!.on('root.set', this.#handleRootChange);

    this.#rootElement = this.#navigatedViewer?.get('canvas')?.getRootElement();
  };

  render = async (options: RenderOptions) => {
    const {
      container,
      xml,
      selectableElements: unfilteredSelectableElements = [],
      selectedElementIds: unfilteredSelectedElementIds,
      overlaysData = [],
      highlightedSequenceFlows = [],
      highlightedElementIds: unfilteredHighlightedElementIds = [],
      nonSelectableNodeTooltipText,
      hasOuterBorderOnSelection,
      customElementClasses: unfilteredCustomElementClasses = [],
    } = options;

    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (this.#xml !== xml) {
      this.#xml = xml;
      await this.import(xml);
    }

    const doesElementExist = (elementId: string) => {
      return (
        this.#navigatedViewer?.get('elementRegistry')?.get(elementId) !==
        undefined
      );
    };
    const selectedElementIds =
      unfilteredSelectedElementIds?.filter(doesElementExist);
    const highlightedElementIds =
      unfilteredHighlightedElementIds?.filter(doesElementExist);
    const selectableElements =
      unfilteredSelectableElements?.filter(doesElementExist);
    const customElementClasses = unfilteredCustomElementClasses?.filter(
      ([elementId]) => doesElementExist(elementId),
    );

    // if render is called a second time before importing has finished,
    // exit early because there is no root element yet.
    if (this.#rootElement === undefined) {
      return;
    }

    if (!isEqual(this.#selectableElements, selectableElements)) {
      // handle op-selectable markers
      this.#selectableElements.forEach((elementId) => {
        this.#removeMarker(elementId, 'op-selectable');
      });
      selectableElements.forEach((elementId) => {
        this.#addMarker(elementId, 'op-selectable');
      });
      this.#selectableElements = selectableElements;

      // handle op-non-selectable markers
      const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
      this.#nonSelectableElements.forEach((elementId) => {
        this.#removeMarker(elementId, 'op-non-selectable');
        this.#removeTooltip(elementId);
      });
      const nonSelectableElements = elementRegistry?.filter((element) =>
        isNonSelectableElement(element, selectableElements),
      );
      nonSelectableElements?.forEach(({id}) => {
        this.#addMarker(id, 'op-non-selectable');
        if (nonSelectableNodeTooltipText !== undefined) {
          this.#addTooltip(id, nonSelectableNodeTooltipText);
        }
      });
      this.#nonSelectableElements = nonSelectableElements
        ? nonSelectableElements.map(({id}) => id)
        : [];
    }

    // handle op-selected markers and selected element ref
    if (
      !isEqual(this.#selectedElementIds?.sort(), selectedElementIds?.sort())
    ) {
      if (this.#selectedElementIds !== undefined) {
        this.#selectedElementIds.forEach((elementId) => {
          this.#removeMarker(elementId, 'op-selected');
          this.#removeMarker(elementId, 'op-selected-frame');
        });

        this.selectedElement = undefined;
      }

      const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
      const canvas = this.#navigatedViewer?.get('canvas');

      if (selectedElementIds !== undefined) {
        selectedElementIds.forEach((elementId) => {
          this.#addMarker(elementId, 'op-selected');
          if (hasOuterBorderOnSelection) {
            this.#addMarker(elementId, 'op-selected-frame');
          }
          this.selectedElement = elementRegistry?.getGraphics(elementId);

          if (canvas !== undefined) {
            const selectedElementIdRootElement = canvas.findRoot(elementId);

            if (
              selectedElementIdRootElement !== undefined &&
              this.#rootElement?.id !== selectedElementIdRootElement.id
            ) {
              canvas.setRootElement(selectedElementIdRootElement);
            }
          }
        });
      }

      this.#selectedElementIds = selectedElementIds;
    }

    if (
      selectedElementIds !== undefined &&
      this.#hasOuterBorderOnSelection !== hasOuterBorderOnSelection
    ) {
      selectedElementIds.forEach((elementId) => {
        this.#removeMarker(elementId, 'op-selected-frame');
        if (hasOuterBorderOnSelection) {
          this.#addMarker(elementId, 'op-selected-frame');
        }
      });

      this.#hasOuterBorderOnSelection = hasOuterBorderOnSelection;
    }

    // handle op-highlighted element markers
    if (!isEqual(this.#highlightedElementIds, highlightedElementIds)) {
      // remove previous markers
      this.#highlightedElementIds.forEach((elementId) => {
        this.#removeMarker(elementId, 'op-highlighted');
      });

      // add new markers
      highlightedElementIds.forEach((element) => {
        this.#addMarker(element, 'op-highlighted');
      });

      this.#highlightedElementIds = highlightedElementIds;
    }

    // handle custom element classes
    if (!isEqual(this.#customElementClasses, customElementClasses)) {
      this.#customElementClasses.forEach(([elementId, className]) => {
        this.#removeMarker(elementId, className);
      });
      customElementClasses.forEach(([elementId, className]) => {
        this.#addMarker(elementId, className);
      });
      this.#customElementClasses = customElementClasses;
    }

    // handle overlays
    if (!isEqual(this.#overlaysData, overlaysData)) {
      [
        ...new Set(
          [...this.#overlaysData, ...overlaysData].map(({type}) => type),
        ),
      ].forEach((type) => {
        this.#removeOverlays(type);
        diagramOverlaysStore.removeOverlay(type);
      });

      this.#overlaysData = overlaysData;

      overlaysData.forEach(
        ({payload, elementId, position, type, isZoomFixed}) => {
          const container = document.createElement('div');

          this.#attachOverlay({
            elementId: elementId,
            children: container,
            position,
            type,
            isZoomFixed,
          });

          diagramOverlaysStore.addOverlay({
            container,
            payload,
            elementId: elementId,
            type,
          });
        },
      );
    }

    // handle highlighted sequence flows
    if (!isEqual(this.#highlightedSequenceFlows, highlightedSequenceFlows)) {
      highlightedSequenceFlows.forEach((sequenceFlow) => {
        this.#colorSequenceFlow(sequenceFlow, highlightedSequenceFlowsColor);
      });

      this.#highlightedSequenceFlows = highlightedSequenceFlows;
    }
  };

  #createViewer = (container: HTMLElement) => {
    this.#destroy();
    this.#navigatedViewer = new NavigatedViewer({
      container,
      bpmnRenderer: bpmnRendererColors,
      canvas: {deferUpdate: true},
      additionalModules: [
        ElementTemplatesIconsRenderer,
        OutlineModule,
        minimapModule,
      ],
    });
  };

  #addMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.addMarker(elementId, className);
    } else {
      throw new Error(`Element "${elementId}" not found`);
    }
  };

  #addTooltip = (elementId: string, tooltipText: string) => {
    const titleElement = document.querySelector(
      `[data-element-id="${elementId}"] title`,
    );

    if (titleElement === null) {
      const tooltip = document.createElementNS(
        'http://www.w3.org/2000/svg',
        'title',
      );

      tooltip.textContent = tooltipText;
      document
        .querySelector(`[data-element-id="${elementId}"]`)
        ?.appendChild(tooltip);
    }
  };

  #removeTooltip = (elementId: string) => {
    const titleElement = document.querySelector(
      `[data-element-id="${elementId}"] title`,
    );
    if (titleElement !== null) {
      document
        .querySelector(`[data-element-id="${elementId}"]`)
        ?.removeChild(titleElement);
    }
  };

  #colorSequenceFlow = (id: string, color: string) => {
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
    const graphicsFactory = this.#navigatedViewer?.get('graphicsFactory');
    const element = elementRegistry?.get(id);

    if (element?.di !== undefined) {
      element.di.set('border-color', color);

      const gfx = elementRegistry?.getGraphics(element);
      if (gfx !== undefined) {
        graphicsFactory?.update('connection', element, gfx);
      }
    }
  };

  #attachOverlay = ({
    elementId,
    children,
    position,
    type,
    isZoomFixed = false,
  }: {
    elementId: string;
    children: HTMLElement;
    position: OverlayPosition;
    type: string;
    isZoomFixed?: boolean;
  }) => {
    if (
      this.#navigatedViewer?.get('elementRegistry')?.get(elementId) ===
      undefined
    ) {
      return null;
    }

    return this.#navigatedViewer?.get('overlays')?.add(elementId, type, {
      html: children,
      position: position,
      ...(isZoomFixed ? {scale: {min: 1, max: 1}} : {}),
    });
  };

  #removeOverlays = (type: string) => {
    this.#navigatedViewer?.get('overlays')?.remove({type});
  };

  #removeMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.removeMarker(elementId, className);
    }
  };

  #handleElementClick = (event: Event) => {
    const element = event.element;
    if (
      isNonSelectableElement(element, this.#selectableElements) ||
      this.#selectableElements.length === 0
    ) {
      return;
    }
    if (
      this.#selectableElements.includes(element.id) &&
      (this.#selectedElementIds === undefined ||
        !this.#selectedElementIds.includes(element.id))
    ) {
      this.onElementSelection?.(
        element.id,
        isMultiInstance(element.businessObject),
      );
    } else if (this.#selectedElementIds !== undefined) {
      this.onElementSelection?.(undefined);
    }
  };

  #handleElementDoubleClick = (event: Event) => {
    const element = event.element;

    if (element.type === 'label') {
      return;
    }

    if (
      this.#customElementClasses.some(([elementId]) => elementId === element.id)
    ) {
      this.onElementDoubleClick?.(element.id);
    }
  };

  #handleRootChange = () => {
    this.#rootElement = this.#navigatedViewer?.get('canvas')?.getRootElement();

    if (this.#rootElement !== undefined) {
      this.onRootChange?.(this.#rootElement.businessObject.id);
    }
  };

  #destroy = () => {
    this.#navigatedViewer?.destroy();
  };

  reset = () => {
    this.onElementSelection = undefined;
    this.onElementDoubleClick = undefined;
    this.onViewboxChange = undefined;
    this.#destroy();
  };

  zoom = (step: number) => {
    this.#navigatedViewer?.get('zoomScroll')?.stepZoom(step);
  };

  zoomIn = () => {
    tracking.track({eventName: 'diagram-zoom-in'});
    this.zoom(0.1);
  };

  zoomOut = () => {
    tracking.track({eventName: 'diagram-zoom-out'});
    this.zoom(-0.1);
  };

  zoomReset = () => {
    const canvas = this.#navigatedViewer?.get('canvas');

    tracking.track({eventName: 'diagram-zoom-reset'});

    if (canvas !== undefined) {
      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }
  };

  findRootId = (selectedElementId: string) => {
    return this.#navigatedViewer?.get('canvas')?.findRoot(selectedElementId)
      ?.businessObject.id;
  };

  notifyResize = () => {
    const canvas = this.#navigatedViewer?.get('canvas');
    if (canvas) {
      canvas.resized();
    }
  };

  toggleMinimap = () => {
    const minimap = this.#navigatedViewer?.get('minimap');
    if (minimap) {
      const isOpen = minimap.isOpen();
      if (isOpen) {
        minimap.close();
      } else {
        minimap.open();
      }
    }
  };

  isMinimapOpen = (): boolean => {
    const minimap = this.#navigatedViewer?.get('minimap');
    if (minimap) {
      return minimap.isOpen() ?? false;
    }
    return false;
  };
}

export {BpmnJS};
export type {OnElementSelection, OnElementDoubleClick};
export type {OverlayData} from './overlayTypes';
