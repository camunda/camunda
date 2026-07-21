/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {DmnJS, type Definitions} from './DmnJS';
import {Container, ViewerCanvas} from './styled';

type Props = {
	xml: string | null;
	decisionViewId: string | null;
	highlightableRules?: number[];
	onDefinitionsChange?: (definitions: Definitions | undefined) => void;
};

function DecisionViewer({xml, decisionViewId, highlightableRules = [], onDefinitionsChange}: Props) {
	const dmnJSRef = useRef<DmnJS | null>(null);
	const viewerCanvasRef = useRef<HTMLDivElement | null>(null);

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

		dmnJSRef.current!.render(viewerCanvasRef.current, xml, decisionViewId);
	}, [decisionViewId, xml]);

	useEffect(() => {
		return () => {
			dmnJSRef.current?.reset();
		};
	}, []);

	return (
		<Container $highlightableRows={highlightableRules} data-testid="decision-viewer">
			<ViewerCanvas ref={viewerCanvasRef} />
		</Container>
	);
}

export {DecisionViewer};
