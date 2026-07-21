/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import 'dmn-js-shared/assets/css/dmn-js-shared.css';
import 'dmn-js-decision-table/assets/css/dmn-js-decision-table.css';
import 'dmn-js-literal-expression/assets/css/dmn-js-literal-expression.css';
import 'dmn-font/dist/css/dmn.css';
import Manager from 'dmn-js-shared/lib/base/Manager';
// @ts-expect-error no type declarations for this package
import {is} from 'dmn-js-shared/lib/util/ModelUtil';
// @ts-expect-error no type declarations for this package
import DecisionTableViewer from 'dmn-js-decision-table/lib/Viewer';
// @ts-expect-error no type declarations for this package
import LiteralExpressionViewer from 'dmn-js-literal-expression/lib/Viewer';
import {logger} from '#/operate/shared/utils/logger';

type Element = {decisionLogic?: {$type: string}};

type Definitions = {id: string; name: string};

class DecisionManager extends Manager {
	_getViewProviders() {
		return [
			{
				id: 'decisionTable',
				constructor: DecisionTableViewer,
				opens(element: Element) {
					return is(element, 'dmn:Decision') && is(element.decisionLogic, 'dmn:DecisionTable');
				},
			},
			{
				id: 'literalExpression',
				constructor: LiteralExpressionViewer,
				opens(element: Element) {
					return is(element, 'dmn:Decision') && is(element.decisionLogic, 'dmn:LiteralExpression');
				},
			},
		];
	}
}

class DmnJS {
	#xml: string | null = null;
	#manager: DecisionManager | null = null;
	#decisionViewId: string | null = null;

	onDefinitionsChange?: (definitions: Definitions | undefined) => void;

	render = async (container: HTMLElement, xml: string, decisionViewId: string) => {
		if (this.#manager === null) {
			this.#manager = new DecisionManager({container});
		}

		if (this.#xml !== xml) {
			this.#manager.destroy();
			this.#manager = new DecisionManager({container});

			await this.#manager.importXML(xml);
			this.onDefinitionsChange?.(this.#manager.getDefinitions());
		}

		if (this.#decisionViewId !== decisionViewId || this.#xml !== xml) {
			const view = this.#manager.getViews().find((view) => view.id === decisionViewId);

			if (view !== undefined) {
				this.#manager.open(view);
				this.#decisionViewId = decisionViewId;
			} else {
				logger.error(`decision "${decisionViewId}" not found in xml`);
			}
		}

		this.#xml = xml;
	};

	reset = () => {
		this.#xml = null;
		this.#decisionViewId = null;
		this.onDefinitionsChange = undefined;
		this.#manager?.destroy();
		this.#manager = null;
	};
}

export {DmnJS};
export type {Definitions};
