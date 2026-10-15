/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {DmnJS, type Definitions} from '../DmnJS';

type Props = {
	xml: string | null;
	decisionViewId: string | null;
	highlightableRules?: number[];
	onDefinitionsChange?: (definitions: Definitions | undefined) => void;
};

function addRuleIndexColumnHeader(container: HTMLElement) {
	container.querySelectorAll('th.index-column').forEach((ruleIndexColumnHeader) => {
		if (ruleIndexColumnHeader instanceof HTMLTableCellElement && ruleIndexColumnHeader.textContent?.trim() === '') {
			ruleIndexColumnHeader.textContent = '#';
		}
	});
}

function markHighlightedRules(container: HTMLElement, highlightableRules: number[]) {
	container.querySelectorAll('tbody tr').forEach((row, index) => {
		const isHighlighted = highlightableRules.includes(index + 1);
		if (row.hasAttribute('data-highlighted') !== isHighlighted) {
			row.toggleAttribute('data-highlighted', isHighlighted);
		}
	});
}

function DecisionViewer({xml, decisionViewId, highlightableRules = [], onDefinitionsChange}: Props) {
	const dmnJSRef = useRef<DmnJS | null>(null);
	const viewerCanvasRef = useRef<HTMLDivElement | null>(null);
	const highlightableRulesRef = useRef(highlightableRules);
	const highlightKey = highlightableRules.join(',');

	if (dmnJSRef.current === null) {
		dmnJSRef.current = new DmnJS();
	}

	useEffect(() => {
		dmnJSRef.current!.onDefinitionsChange = onDefinitionsChange;
	}, [onDefinitionsChange]);

	useEffect(() => {
		if (viewerCanvasRef.current === null || xml === null || decisionViewId === null) {
			return;
		}

		const viewerCanvas = viewerCanvasRef.current;
		const observer = new MutationObserver(() => {
			addRuleIndexColumnHeader(viewerCanvas);
			markHighlightedRules(viewerCanvas, highlightableRulesRef.current);
		});
		observer.observe(viewerCanvas, {childList: true, subtree: true});
		void dmnJSRef.current!.render(viewerCanvas, xml, decisionViewId).then(() => {
			addRuleIndexColumnHeader(viewerCanvas);
			markHighlightedRules(viewerCanvas, highlightableRulesRef.current);
		});

		return () => {
			observer.disconnect();
		};
	}, [decisionViewId, xml]);

	useEffect(() => {
		highlightableRulesRef.current = highlightableRules;
		if (viewerCanvasRef.current !== null) {
			markHighlightedRules(viewerCanvasRef.current, highlightableRules);
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps -- keyed on content to ignore array identity
	}, [highlightKey]);

	useEffect(() => {
		return () => {
			dmnJSRef.current?.reset();
		};
	}, []);

	return (
		<div data-testid="decision-viewer" className="dmn-viewer-theme h-full min-h-[200px] w-full px-6 py-8">
			<div ref={viewerCanvasRef} className="h-full min-h-[200px]" />
		</div>
	);
}

export {DecisionViewer};
