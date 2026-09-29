/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import 'dmn-js-shared/assets/css/dmn-js-shared.css';
import 'dmn-js-drd/assets/css/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
import {useEffect, useMemo, useRef, useState} from 'react';
import {StateOverlay} from '#/operate/shared/StateOverlay/StateOverlay';
import {DrdJS, type DecisionStateOverlay} from './DrdJS';
import type {DrdData} from './drdData.queries';
import {ViewerCanvas} from './DrdViewer.styled';

type Props = {
	xml: string;
	data: DrdData;
	selectedDecisionEvaluationInstanceKey: string;
	selectedDecisionDefinitionId?: string;
	onDecisionSelection: (decisionEvaluationInstanceKey: string) => void;
	onDefinitionsChange?: (definitions: {id: string; name: string} | undefined) => void;
	onError?: (error: Error) => void;
};

function DrdViewer({
	xml,
	data,
	selectedDecisionEvaluationInstanceKey,
	selectedDecisionDefinitionId,
	onDecisionSelection,
	onDefinitionsChange,
	onError,
}: Props) {
	const canvasRef = useRef<HTMLDivElement>(null);
	const viewerRef = useRef<DrdJS>(null);
	const onErrorRef = useRef(onError);
	const [overlays, setOverlays] = useState<DecisionStateOverlay[]>([]);
	const [failure, setFailure] = useState<{
		error: Error;
		signature: string;
	} | null>(null);
	const inputSignature = useMemo(
		() =>
			JSON.stringify({
				xml,
				selectedDecisionEvaluationInstanceKey,
				selectedDecisionDefinitionId,
				data: Object.keys(data)
					.sort()
					.map((id) => data[id]),
			}),
		[xml, selectedDecisionEvaluationInstanceKey, selectedDecisionDefinitionId, data],
	);

	if (viewerRef.current === null) {
		viewerRef.current = new DrdJS();
	}
	if (failure !== null && failure.signature === inputSignature) {
		throw failure.error;
	}

	useEffect(() => {
		const viewer = viewerRef.current!;
		viewer.onDecisionSelection = onDecisionSelection;
		viewer.onDefinitionsChange = onDefinitionsChange;
		viewer.onOverlayChange = setOverlays;
	}, [onDecisionSelection, onDefinitionsChange]);

	useEffect(() => {
		onErrorRef.current = onError;
	}, [onError]);

	useEffect(() => {
		let isActive = true;
		if (canvasRef.current !== null) {
			void viewerRef
				.current!.render(
					canvasRef.current,
					xml,
					data,
					selectedDecisionEvaluationInstanceKey,
					selectedDecisionDefinitionId,
				)
				.then(() => {
					if (isActive) {
						setFailure(null);
					}
				})
				.catch((renderError: Error) => {
					if (isActive) {
						if (onErrorRef.current !== undefined) {
							onErrorRef.current(renderError);
						} else {
							setFailure({error: renderError, signature: inputSignature});
						}
					}
				});
		}
		return () => {
			isActive = false;
		};
	}, [data, inputSignature, selectedDecisionDefinitionId, selectedDecisionEvaluationInstanceKey, xml]);

	useEffect(() => {
		return () => {
			viewerRef.current!.onOverlayChange = undefined;
			viewerRef.current!.reset();
		};
	}, []);

	return (
		<ViewerCanvas data-testid="drd-viewer" ref={canvasRef}>
			{overlays.map(({decisionDefinitionId, state, container}) => (
				<StateOverlay key={decisionDefinitionId} state={state} container={container} />
			))}
		</ViewerCanvas>
	);
}

export {DrdViewer};
