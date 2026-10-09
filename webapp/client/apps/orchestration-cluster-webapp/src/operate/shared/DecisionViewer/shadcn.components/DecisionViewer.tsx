/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {cn} from '#/shared/cn';
import {DmnJS} from '../DmnJS';

type Props = {
	xml: string | null;
	decisionViewId: string | null;
};

const DECISION_TABLE_CLASSES = cn(
	'[&_.dmn-decision-table-container]:[--decision-table-font-family:inherit]!',
	'[&_.dmn-decision-table-container]:[--table-head-clause-color:var(--foreground)]!',
	'[&_.dmn-decision-table-container]:[--table-head-variable-color:var(--foreground)]!',
	'[&_.dmn-decision-table-container]:[--table-head-separator-color:var(--neutral-border-subtle)]!',
	'[&_.dmn-decision-table-container]:[--table-color:var(--foreground)]!',
	'[&_.dmn-decision-table-container]:[--table-cell-color:var(--foreground)]!',
	'[&_.dmn-decision-table-container]:[--decision-table-color:var(--foreground)]!',
	'[&_.dmn-decision-table-container]:[--decision-table-properties-color:var(--foreground)]!',
	'[&_.dmn-decision-table-container]:[--table-head-border-color:var(--neutral-border-strong)]!',
	'[&_.dmn-decision-table-container]:[--table-cell-border-color:var(--neutral-border-strong)]!',
	'[&_.dmn-decision-table-container]:[--decision-table-background-color:var(--background)]!',
	'[&_.dmn-decision-table-container]:[--table-row-alternative-background-color:var(--background)]!',
	'[&_.decision-table-properties]:border-[2px_2px_1px_2px]',
	'[&_.tjs-table-container]:bg-background! [&_.tjs-table-container]:border-[2px_2px_1px_2px]',
);

const LITERAL_EXPRESSION_CLASSES = cn(
	'[&_.dmn-literal-expression-container]:[--literal-expression-font-family:inherit]!',
	'[&_.dmn-literal-expression-container]:[--literal-expression-font-family-monospace:var(--font-mono)]!',
	'[&_.dmn-literal-expression-container]:[--decision-properties-border-color:var(--neutral-border-strong)]!',
	'[&_.dmn-literal-expression-container]:[--decision-properties-color:var(--foreground)]!',
	'[&_.dmn-literal-expression-container]:[--textarea-color:var(--foreground)]!',
	'[&_.dmn-literal-expression-container]:[--literal-expression-properties-color:var(--foreground)]!',
	'[&_.decision-properties]:bg-background! [&_.decision-properties]:border-[var(--neutral-border-strong)]! [&_.decision-properties]:border-[2px_2px_1px_2px]',
	'[&_.textarea]:bg-background! [&_.textarea]:border-[var(--neutral-border-strong)]! [&_.textarea]:min-h-0 [&_.textarea]:border-[1px_2px]',
	'[&_.literal-expression-properties]:bg-background! [&_.literal-expression-properties]:border-[var(--neutral-border-strong)]! [&_.literal-expression-properties]:border-[1px_2px_2px_2px]',
	'[&_.dmn-literal-expression-container_table]:[border-collapse:unset] [&_.dmn-literal-expression-container_table]:p-0',
);

function addRuleIndexColumnHeader(container: HTMLElement) {
	container.querySelectorAll('th.index-column').forEach((ruleIndexColumnHeader) => {
		if (ruleIndexColumnHeader instanceof HTMLTableCellElement && ruleIndexColumnHeader.textContent?.trim() === '') {
			ruleIndexColumnHeader.textContent = '#';
		}
	});
}

function DecisionViewer({xml, decisionViewId}: Props) {
	const dmnJSRef = useRef<DmnJS | null>(null);
	const viewerCanvasRef = useRef<HTMLDivElement | null>(null);

	if (dmnJSRef.current === null) {
		dmnJSRef.current = new DmnJS();
	}

	useEffect(() => {
		if (viewerCanvasRef.current === null || xml === null || decisionViewId === null) {
			return;
		}

		const viewerCanvas = viewerCanvasRef.current;
		const observer = new MutationObserver(() => {
			addRuleIndexColumnHeader(viewerCanvas);
		});
		observer.observe(viewerCanvas, {childList: true, subtree: true});
		void dmnJSRef.current!.render(viewerCanvas, xml, decisionViewId).then(() => {
			addRuleIndexColumnHeader(viewerCanvas);
		});

		return () => {
			observer.disconnect();
		};
	}, [decisionViewId, xml]);

	useEffect(() => {
		return () => {
			dmnJSRef.current?.reset();
		};
	}, []);

	return (
		<div
			data-testid="decision-viewer"
			className={cn(
				'h-full min-h-[200px] w-full bg-neutral-background-medium px-6 py-8 [&_.powered-by]:hidden',
				DECISION_TABLE_CLASSES,
				LITERAL_EXPRESSION_CLASSES,
			)}
		>
			<div ref={viewerCanvasRef} className="h-full min-h-[200px]" />
		</div>
	);
}

export {DecisionViewer};
