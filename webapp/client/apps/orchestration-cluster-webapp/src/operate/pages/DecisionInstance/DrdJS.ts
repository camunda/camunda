/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Manager from 'dmn-js-shared/lib/base/Manager';
// @ts-expect-error no type declarations for this package
import {containsDi} from 'dmn-js-shared/lib/util/DiUtil';
// @ts-expect-error no type declarations for this package
import {is} from 'dmn-js-shared/lib/util/ModelUtil';
// @ts-expect-error no type declarations for this package
import NavigatedViewer from 'dmn-js-drd/lib/NavigatedViewer';
import isEqual from 'lodash/isEqual';
import type {DrdData} from './drdData.queries';

type DecisionStateOverlay = {
	decisionDefinitionId: string;
	state: DrdData[string]['state'];
	container: HTMLDivElement;
};

function Outline(eventBus: {
	on: (event: string, callback: (event: {element: {width: number; height: number}; gfx: SVGGElement}) => void) => void;
}) {
	eventBus.on('shape.added', ({element, gfx}) => {
		const outline = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
		outline.setAttribute('class', 'djs-outline');
		outline.setAttribute('fill', 'none');
		outline.setAttribute('x', '-4');
		outline.setAttribute('y', '-4');
		outline.setAttribute('width', String(element.width + 8));
		outline.setAttribute('height', String(element.height + 8));
		gfx.appendChild(outline);
	});
}

Outline.$inject = ['eventBus'];

class DrdManager extends Manager {
	_getViewProviders() {
		return [
			{
				id: 'drd',
				constructor: NavigatedViewer,
				opens(element: unknown) {
					return is(element, 'dmn:Definitions') && containsDi(element);
				},
			},
		];
	}
}

class DrdJS {
	#xml: string | null = null;
	#manager: DrdManager | null = null;
	#activeViewer: ReturnType<DrdManager['getActiveViewer']> = undefined;
	#data: DrdData = {};
	#selectedDecisionId: string | null = null;
	#generation = 0;
	#renderVersion = 0;
	#importPromise: Promise<void> | null = null;

	onDecisionSelection?: (decisionEvaluationInstanceKey: string) => void;
	onDefinitionsChange?: (definitions: {id: string; name: string} | undefined) => void;
	onOverlayChange?: (overlays: DecisionStateOverlay[]) => void;

	#handleDecisionSelection = ({element}: {element: {id: string}}) => {
		if (Object.hasOwn(this.#data, element.id)) {
			this.onDecisionSelection?.(this.#data[element.id]!.decisionEvaluationInstanceKey);
		}
	};

	render = async (
		container: HTMLElement,
		xml: string,
		data: DrdData,
		selectedDecisionEvaluationInstanceKey: string,
		selectedDecisionDefinitionId?: string,
	) => {
		const renderVersion = ++this.#renderVersion;
		if (this.#xml !== xml || this.#manager === null) {
			const generation = ++this.#generation;
			this.#activeViewer?.off('element.click', this.#handleDecisionSelection);
			this.#activeViewer = undefined;
			this.#manager?.destroy();
			this.#manager = new DrdManager({
				container,
				drd: {
					additionalModules: [
						{__init__: ['outline'], outline: ['type', Outline]},
						{definitionPropertiesView: ['value', null]},
					],
					drdRenderer: {
						defaultFillColor: 'var(--cds-layer)',
						defaultStrokeColor: 'var(--cds-icon-secondary)',
						defaultLabelColor: 'var(--cds-text-primary)',
					},
				},
			});
			this.#xml = xml;
			this.#data = {};
			this.#selectedDecisionId = null;
			this.onOverlayChange?.([]);
			const manager = this.#manager;
			this.#importPromise = manager.importXML(xml).then(() => {
				if (generation !== this.#generation) {
					return;
				}
				this.onDefinitionsChange?.(manager.getDefinitions());
				const activeViewer = manager.getActiveViewer();
				if (activeViewer === undefined) {
					throw new Error('DRD view is unavailable for the decision definition');
				}
				activeViewer.on('element.click', this.#handleDecisionSelection);
				this.#activeViewer = activeViewer;
				const canvas = activeViewer.get('canvas');
				canvas.resized();
				canvas.zoom('fit-viewport', 'auto');
			});
		}

		try {
			await this.#importPromise;
		} catch (error) {
			if (renderVersion !== this.#renderVersion) {
				return;
			}
			this.#activeViewer?.off('element.click', this.#handleDecisionSelection);
			this.#activeViewer = undefined;
			this.#manager?.destroy();
			this.#manager = null;
			this.#xml = null;
			this.#importPromise = null;
			throw error;
		}
		if (renderVersion !== this.#renderVersion) {
			return;
		}

		const activeViewer = this.#manager.getActiveViewer();
		if (activeViewer === undefined) {
			throw new Error('DRD view is unavailable for the decision definition');
		}
		const canvas = activeViewer.get('canvas');
		const registry = activeViewer.get('elementRegistry');
		const isVisible = (id: string) => registry.get(id) !== undefined;
		const previousIds = Object.keys(this.#data).filter(isVisible).sort();
		const nextIds = Object.keys(data).filter(isVisible).sort();
		if (!isEqual(previousIds, nextIds)) {
			previousIds.forEach((id) => canvas.removeMarker(id, 'ope-selectable'));
			nextIds.forEach((id) => canvas.addMarker(id, 'ope-selectable'));
		}

		const selectedDecisionIdFromData =
			Object.values(data).find(
				(instance) => instance.decisionEvaluationInstanceKey === selectedDecisionEvaluationInstanceKey,
			)?.decisionDefinitionId ?? null;
		const selectedId = selectedDecisionDefinitionId ?? selectedDecisionIdFromData;
		const selectedDecisionId = selectedId !== null && isVisible(selectedId) ? selectedId : null;
		if (this.#selectedDecisionId !== selectedDecisionId) {
			if (this.#selectedDecisionId !== null) {
				canvas.removeMarker(this.#selectedDecisionId, 'ope-selected');
			}
			if (selectedDecisionId !== null) {
				canvas.addMarker(selectedDecisionId, 'ope-selected');
			}
			this.#selectedDecisionId = selectedDecisionId;
		}

		if (
			!isEqual(
				previousIds.map((id) => this.#data[id]),
				nextIds.map((id) => data[id]),
			)
		) {
			const overlays = activeViewer.get('overlays');
			overlays.remove({type: 'decisionState'});
			this.onOverlayChange?.(
				nextIds.map((id) => {
					const {state} = data[id]!;
					const overlayContainer = document.createElement('div');
					overlays.add(id, 'decisionState', {
						position: {bottom: 12, left: -12},
						html: overlayContainer,
					});
					return {decisionDefinitionId: id, state, container: overlayContainer};
				}),
			);
		}

		this.#data = data;
	};

	reset = () => {
		++this.#generation;
		++this.#renderVersion;
		this.#xml = null;
		this.#importPromise = null;
		this.#data = {};
		this.#selectedDecisionId = null;
		this.#activeViewer?.off('element.click', this.#handleDecisionSelection);
		this.#activeViewer = undefined;
		this.#manager?.destroy();
		this.#manager = null;
	};
}

export {DrdJS};
export type {DecisionStateOverlay};
