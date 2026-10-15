/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
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
	container.querySelectorAll('.tjs-table-container tbody tr').forEach((row, index) => {
		const isHighlighted = highlightableRules.includes(index + 1);
		if (row.hasAttribute('data-highlighted') !== isHighlighted) {
			row.toggleAttribute('data-highlighted', isHighlighted);
			if (isHighlighted) {
				row.setAttribute('aria-current', 'true');
			} else {
				row.removeAttribute('aria-current');
			}
		}
	});
}

function DecisionViewer({xml, decisionViewId, highlightableRules = [], onDefinitionsChange}: Props) {
	const [dmnJS] = useState(() => new DmnJS());
	const viewerCanvasRef = useRef<HTMLDivElement | null>(null);
	const [renderError, setRenderError] = useState<unknown>(null);
	const highlightableRulesRef = useRef(highlightableRules);
	const highlightKey = highlightableRules.join(',');

	if (renderError !== null) {
		throw renderError;
	}

	useEffect(() => {
		dmnJS.setOnDefinitionsChange(onDefinitionsChange);
	}, [dmnJS, onDefinitionsChange]);

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
		let isStale = false;
		dmnJS
			.render(viewerCanvas, xml, decisionViewId)
			.then(() => {
				if (!isStale) {
					addRuleIndexColumnHeader(viewerCanvas);
					markHighlightedRules(viewerCanvas, highlightableRulesRef.current);
				}
			})
			.catch((error: unknown) => {
				// A render superseded by a selection change or unmount rejects once the viewer is reset
				if (!isStale) {
					setRenderError(error);
				}
			});

		return () => {
			isStale = true;
			observer.disconnect();
		};
	}, [dmnJS, decisionViewId, xml]);

	useEffect(() => {
		const rules = highlightKey === '' ? [] : highlightKey.split(',').map(Number);
		highlightableRulesRef.current = rules;
		if (viewerCanvasRef.current !== null) {
			markHighlightedRules(viewerCanvasRef.current, rules);
		}
	}, [highlightKey]);

	useEffect(() => {
		return () => {
			dmnJS.reset();
		};
	}, [dmnJS]);

	return (
		<div data-testid="decision-viewer" className="dmn-viewer-theme h-full min-h-[200px] w-full px-6 py-8">
			<div ref={viewerCanvasRef} className="h-full min-h-[200px]" />
		</div>
	);
}

export {DecisionViewer};
