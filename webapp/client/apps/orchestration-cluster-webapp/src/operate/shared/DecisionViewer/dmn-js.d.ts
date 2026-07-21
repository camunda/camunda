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
		type: 'literalExpression' | 'decisionTable';
	};

	declare class Manager {
		constructor(options: {container?: HTMLElement});
		importXML(xml: string): Promise<unknown>;
		destroy(): void;
		getViews(): View[];
		open(view: View): void;
		getDefinitions(): {id: string; name: string} | undefined;
	}

	export = Manager;
}
