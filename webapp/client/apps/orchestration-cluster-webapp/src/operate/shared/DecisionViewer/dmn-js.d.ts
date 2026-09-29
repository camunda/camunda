/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

declare module 'dmn-js-shared/lib/base/Manager' {
	export type View = {
		id: string;
		type: 'literalExpression' | 'decisionTable' | 'drd';
	};

	type DrdCanvas = {
		addMarker(elementId: string, marker: string): void;
		removeMarker(elementId: string, marker: string): void;
		resized(): void;
		zoom(level: number | 'fit-viewport', position?: 'auto' | {x: number; y: number}): number;
	};

	type DrdOverlays = {
		add(elementId: string, type: string, config: {position: {bottom: number; left: number}; html: HTMLElement}): void;
		remove(filter: {type: string}): void;
	};

	type DrdElementRegistry = {
		get(elementId: string): {id: string} | undefined;
	};

	type ActiveDrdViewer = {
		on(event: 'element.click', callback: (event: {element: {id: string}}) => void): void;
		off(event: 'element.click', callback: (event: {element: {id: string}}) => void): void;
		get(service: 'canvas'): DrdCanvas;
		get(service: 'overlays'): DrdOverlays;
		get(service: 'elementRegistry'): DrdElementRegistry;
	};

	declare class Manager {
		constructor(options: {
			container?: HTMLElement;
			drd?: {additionalModules: object[]; drdRenderer: Record<string, string>};
		});
		importXML(xml: string): Promise<unknown>;
		destroy(): void;
		getViews(): View[];
		open(view: View): void;
		getDefinitions(): {id: string; name: string} | undefined;
		getActiveViewer(): ActiveDrdViewer | undefined;
	}

	export = Manager;
}
