/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import 'bpmn-js/dist/assets/bpmn-js.css';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import OutlineModule from 'bpmn-js/lib/features/outline';
// @ts-expect-error no type declarations for this package
import ElementTemplatesIconsRenderer from '@bpmn-io/element-template-icon-renderer';

const bpmnRendererColors = {
	outline: {
		fill: 'var(--cds-highlight)',
	},
	defaultFillColor: 'var(--cds-layer)',
	defaultStrokeColor: 'var(--cds-icon-secondary)',
	element: {
		text: 'var(--cds-text-primary)',
		background: {
			default: 'var(--cds-layer)',
		},
	},
};

type RenderOptions = {
	container: HTMLElement;
	xml: string;
};

class BpmnJS {
	#navigatedViewer: InstanceType<typeof NavigatedViewer> | null = null;
	#xml: string | null = null;

	async render({container, xml}: RenderOptions) {
		if (this.#navigatedViewer === null) {
			this.#createViewer(container);
		}

		if (this.#xml !== xml) {
			this.#xml = xml;
			await this.#navigatedViewer!.importXML(xml);
			this.zoomReset();
		}
	}

	#createViewer(container: HTMLElement) {
		this.#destroy();
		this.#navigatedViewer = new NavigatedViewer({
			container,
			bpmnRenderer: bpmnRendererColors,
			canvas: {deferUpdate: true},
			additionalModules: [ElementTemplatesIconsRenderer, OutlineModule],
		});
	}

	addMarker(elementId: string, className: string) {
		const canvas = this.#navigatedViewer?.get('canvas');
		const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

		if (elementRegistry?.get(elementId) !== undefined) {
			canvas?.addMarker(elementId, className);
		}
	}

	zoom(step: number) {
		this.#navigatedViewer?.get('zoomScroll')?.stepZoom(step);
	}

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

	reset() {
		this.#destroy();
		this.#xml = null;
	}

	#destroy() {
		this.#navigatedViewer?.destroy();
		this.#navigatedViewer = null;
	}
}

export {BpmnJS};
