/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {DmnJS} from '../DmnJS';

type Props = {
	xml: string | null;
	decisionViewId: string | null;
};

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
		<div data-testid="decision-viewer" className="dmn-viewer-theme h-full min-h-[200px] w-full px-6 py-8">
			<div ref={viewerCanvasRef} className="h-full min-h-[200px]" />
		</div>
	);
}

export {DecisionViewer};
