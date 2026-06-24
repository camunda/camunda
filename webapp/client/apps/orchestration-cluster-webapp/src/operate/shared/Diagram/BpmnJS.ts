/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import 'bpmn-js/dist/assets/bpmn-js.css';
import NavigatedViewer, {type BpmnElement, type Event, type OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import OutlineModule from 'bpmn-js/lib/features/outline';
// @ts-expect-error no type declarations for this package
import ElementTemplatesIconsRenderer from '@bpmn-io/element-template-icon-renderer';
// @ts-expect-error no type declarations for this package
import minimapModule from 'diagram-js-minimap';
import 'diagram-js-minimap/assets/diagram-js-minimap.css';
import type {OverlayData, OverlayEntry} from './overlayTypes';

const bpmnRendererColors = {
	outline: {
		fill: 'var(--cds-highlight)',
	},
	defaultFillColor: 'var(--cds-layer-01)',
	defaultStrokeColor: 'var(--cds-icon-secondary)',
	element: {
		text: 'var(--cds-text-primary)',
		background: {
			default: 'var(--cds-layer-01)',
		},
	},
};

const highlightedSequenceFlowsColor = 'var(--cds-background-brand)';

type OnElementSelection = (elementId?: string, isMultiInstance?: boolean) => void;
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

function deepEqual(a: unknown, b: unknown): boolean {
	try {
		return JSON.stringify(a) === JSON.stringify(b);
	} catch {
		return false;
	}
}

function isNonSelectableElement(element: BpmnElement, selectableElements: string[]): boolean {
	return (
		selectableElements.length > 0 &&
		!selectableElements.includes(element.id) &&
		element.type !== 'bpmn:SequenceFlow' &&
		element.type !== 'label' &&
		element.parent !== undefined
	);
}

function isMultiInstance(businessObject: BpmnElement['businessObject']): boolean {
	return businessObject?.loopCharacteristics?.$type === 'bpmn:MultiInstanceLoopCharacteristics';
}

class BpmnJS {
	#navigatedViewer: InstanceType<typeof NavigatedViewer> | null = null;
	#xml: string | null = null;
	#selectableElements: string[] = [];
	#nonSelectableElements: string[] = [];
	#selectedElementIds?: string[];
	#highlightedSequenceFlows: string[] = [];
	#highlightedElementIds: string[] = [];
	#overlaysData: OverlayData[] = [];
	#hasOuterBorderOnSelection = false;
	#rootElement?: BpmnElement;
	#customElementClasses: [elementId: string, className: string][] = [];
	#overlaysByType = new Map<string, OverlayEntry[]>();

	selectedElement?: SVGGraphicsElement;
	onElementSelection?: OnElementSelection;
	onElementDoubleClick?: OnElementDoubleClick;
	onViewboxChange?: (isChanging: boolean) => void;
	onRootChange?: (rootElementId: string) => void;
	onOverlayChange?: (overlays: OverlayEntry[]) => void;

	#handleViewboxChanging = () => {
		this.onViewboxChange?.(true);
	};

	#handleViewboxChanged = () => {
		this.onViewboxChange?.(false);
	};

	import = async (xml: string) => {
		this.#navigatedViewer!.off('element.click', this.#handleElementClick);
		this.#navigatedViewer!.off('element.dblclick', this.#handleElementDoubleClick);
		this.#navigatedViewer!.off('canvas.viewbox.changing', this.#handleViewboxChanging);
		this.#navigatedViewer!.off('canvas.viewbox.changed', this.#handleViewboxChanged);
		this.#navigatedViewer!.off('root.set', this.#handleRootChange);

		this.#overlaysData = [];
		this.#selectableElements = [];
		this.#selectedElementIds = undefined;
		this.#hasOuterBorderOnSelection = false;
		this.#customElementClasses = [];
		this.#rootElement = undefined;
		this.#overlaysByType.clear();
		this.onOverlayChange?.([]);

		await this.#navigatedViewer!.importXML(xml);

		this.zoomReset();
		this.#navigatedViewer!.on('element.click', this.#handleElementClick);
		this.#navigatedViewer!.on('element.dblclick', this.#handleElementDoubleClick);
		this.#navigatedViewer!.on('canvas.viewbox.changing', this.#handleViewboxChanging);
		this.#navigatedViewer!.on('canvas.viewbox.changed', this.#handleViewboxChanged);
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

		const doesElementExist = (elementId: string) =>
			this.#navigatedViewer?.get('elementRegistry')?.get(elementId) !== undefined;

		const selectedElementIds = unfilteredSelectedElementIds?.filter(doesElementExist);
		const highlightedElementIds = unfilteredHighlightedElementIds?.filter(doesElementExist);
		const selectableElements = unfilteredSelectableElements?.filter(doesElementExist);
		const customElementClasses = unfilteredCustomElementClasses?.filter(([elementId]) => doesElementExist(elementId));

		if (this.#rootElement === undefined) {
			return;
		}

		if (!deepEqual(this.#selectableElements, selectableElements)) {
			this.#selectableElements.forEach((elementId) => {
				this.#removeMarker(elementId, 'op-selectable');
			});
			selectableElements.forEach((elementId) => {
				this.#addMarker(elementId, 'op-selectable');
			});
			this.#selectableElements = selectableElements;

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
			this.#nonSelectableElements = nonSelectableElements ? nonSelectableElements.map(({id}) => id) : [];
		}

		if (!deepEqual(this.#selectedElementIds?.sort(), selectedElementIds?.sort())) {
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

		if (selectedElementIds !== undefined && this.#hasOuterBorderOnSelection !== hasOuterBorderOnSelection) {
			selectedElementIds.forEach((elementId) => {
				this.#removeMarker(elementId, 'op-selected-frame');
				if (hasOuterBorderOnSelection) {
					this.#addMarker(elementId, 'op-selected-frame');
				}
			});
			this.#hasOuterBorderOnSelection = hasOuterBorderOnSelection;
		}

		if (!deepEqual(this.#highlightedElementIds, highlightedElementIds)) {
			this.#highlightedElementIds.forEach((elementId) => {
				this.#removeMarker(elementId, 'op-highlighted');
			});
			highlightedElementIds.forEach((element) => {
				this.#addMarker(element, 'op-highlighted');
			});
			this.#highlightedElementIds = highlightedElementIds;
		}

		if (!deepEqual(this.#customElementClasses, customElementClasses)) {
			this.#customElementClasses.forEach(([elementId, className]) => {
				this.#removeMarker(elementId, className);
			});
			customElementClasses.forEach(([elementId, className]) => {
				this.#addMarker(elementId, className);
			});
			this.#customElementClasses = customElementClasses;
		}

		if (!deepEqual(this.#overlaysData, overlaysData)) {
			[...new Set([...this.#overlaysData, ...overlaysData].map(({type}) => type))].forEach((type) => {
				this.#removeOverlays(type);
			});

			this.#overlaysData = overlaysData;

			overlaysData.forEach(({payload, elementId, position, type, isZoomFixed}) => {
				const container = document.createElement('div');
				const overlayId = this.#attachOverlay({elementId, children: container, position, type, isZoomFixed});

				if (overlayId !== null) {
					const existing = this.#overlaysByType.get(type) ?? [];
					this.#overlaysByType.set(type, [...existing, {container, payload, elementId, type}]);
				}
			});

			this.#notifyOverlayChange();
		}

		if (!deepEqual(this.#highlightedSequenceFlows, highlightedSequenceFlows)) {
			highlightedSequenceFlows.forEach((sequenceFlow) => {
				this.#colorSequenceFlow(sequenceFlow, highlightedSequenceFlowsColor);
			});
			this.#highlightedSequenceFlows = highlightedSequenceFlows;
		}
	};

	#notifyOverlayChange = () => {
		const allOverlays = [...this.#overlaysByType.values()].flat();
		this.onOverlayChange?.(allOverlays);
	};

	#createViewer = (container: HTMLElement) => {
		this.#destroy();
		this.#navigatedViewer = new NavigatedViewer({
			container,
			bpmnRenderer: bpmnRendererColors,
			canvas: {deferUpdate: true},
			additionalModules: [ElementTemplatesIconsRenderer, OutlineModule, minimapModule],
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
		const titleElement = document.querySelector(`[data-element-id="${elementId}"] title`);
		if (titleElement === null) {
			const tooltip = document.createElementNS('http://www.w3.org/2000/svg', 'title');
			tooltip.textContent = tooltipText;
			document.querySelector(`[data-element-id="${elementId}"]`)?.appendChild(tooltip);
		}
	};

	#removeTooltip = (elementId: string) => {
		const titleElement = document.querySelector(`[data-element-id="${elementId}"] title`);
		if (titleElement !== null) {
			document.querySelector(`[data-element-id="${elementId}"]`)?.removeChild(titleElement);
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
		if (this.#navigatedViewer?.get('elementRegistry')?.get(elementId) === undefined) {
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
		this.#overlaysByType.delete(type);
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
		if (isNonSelectableElement(element, this.#selectableElements) || this.#selectableElements.length === 0) {
			return;
		}
		if (
			this.#selectableElements.includes(element.id) &&
			(this.#selectedElementIds === undefined || !this.#selectedElementIds.includes(element.id))
		) {
			this.onElementSelection?.(element.id, isMultiInstance(element.businessObject));
		} else if (this.#selectedElementIds !== undefined) {
			this.onElementSelection?.(undefined);
		}
	};

	#handleElementDoubleClick = (event: Event) => {
		const element = event.element;
		if (element.type === 'label') {
			return;
		}
		if (this.#customElementClasses.some(([elementId]) => elementId === element.id)) {
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
		this.onOverlayChange = undefined;
		this.#overlaysByType.clear();
		this.#destroy();
	};

	zoom = (step: number) => {
		this.#navigatedViewer?.get('zoomScroll')?.stepZoom(step);
	};

	zoomIn = () => {
		this.zoom(0.1);
	};

	zoomOut = () => {
		this.zoom(-0.1);
	};

	zoomReset = () => {
		const canvas = this.#navigatedViewer?.get('canvas');
		if (canvas !== undefined) {
			canvas.resized();
			canvas.zoom('fit-viewport', 'auto');
		}
	};

	findRootId = (selectedElementId: string) => {
		return this.#navigatedViewer?.get('canvas')?.findRoot(selectedElementId)?.businessObject.id;
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
