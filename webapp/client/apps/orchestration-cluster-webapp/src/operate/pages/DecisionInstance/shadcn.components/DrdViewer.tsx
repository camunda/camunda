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
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {createPortal} from 'react-dom';
import {Scan, ZoomIn, ZoomOut} from '@camunda/design-system/icons';
import {IconButton} from '@camunda/design-system';
import {DrdJS, type DecisionStateOverlay} from '#/operate/shared/DecisionViewer/DrdJS';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';
import type {DrdData} from '../drdData.queries';

type Props = {
	xml: string;
	data: DrdData;
	selectedDecisionEvaluationInstanceKey: string;
	selectedDecisionDefinitionId?: string;
	onDecisionSelection: (decisionEvaluationInstanceKey: string) => void;
	onDefinitionsChange?: (definitions: {id: string; name: string} | undefined) => void;
	onError?: (error: Error) => void;
};

const DRD_COLORS = {
	defaultFillColor: 'var(--viewer-layer)',
	defaultStrokeColor: 'var(--neutral-foreground-subtle)',
	defaultLabelColor: 'var(--neutral-foreground-subtle)',
};

function DecisionStateBadge({
	state,
	container,
	title,
}: {
	state: DrdData[string]['state'];
	container: HTMLElement;
	title: string;
}) {
	return createPortal(
		<span
			data-testid={`state-overlay-${state}`}
			role="img"
			aria-label={title}
			title={title}
			className={cn('inline-flex size-6 items-center justify-center rounded-full bg-[var(--viewer-layer)]')}
		>
			<StateIcon state={state} size={16} />
		</span>,
		container,
	);
}

function DrdViewer({
	xml,
	data,
	selectedDecisionEvaluationInstanceKey,
	selectedDecisionDefinitionId,
	onDecisionSelection,
	onDefinitionsChange,
	onError,
}: Props) {
	const {t} = useTranslation();
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
		viewerRef.current = new DrdJS(DRD_COLORS);
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
		const controller = new AbortController();
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
					if (!controller.signal.aborted) {
						setFailure(null);
					}
				})
				.catch((renderError: Error) => {
					if (!controller.signal.aborted) {
						if (onErrorRef.current !== undefined) {
							onErrorRef.current(renderError);
						} else {
							setFailure({error: renderError, signature: inputSignature});
						}
					}
				});
		}
		return () => {
			controller.abort();
		};
	}, [data, inputSignature, selectedDecisionDefinitionId, selectedDecisionEvaluationInstanceKey, xml]);

	useEffect(() => {
		const canvas = canvasRef.current;
		if (canvas === null) {
			return;
		}
		const observer = new ResizeObserver(() => {
			viewerRef.current?.resize();
			viewerRef.current?.fit();
		});
		observer.observe(canvas);
		return () => observer.disconnect();
	}, []);

	useEffect(() => {
		return () => {
			viewerRef.current!.onOverlayChange = undefined;
			viewerRef.current!.reset();
		};
	}, []);

	return (
		<div className="relative h-full w-full">
			<div data-testid="drd-viewer" ref={canvasRef} className="drd-viewer-theme h-full min-h-[200px] w-full" />
			<div className="absolute bottom-4 left-4 z-10 flex flex-col gap-1">
				<IconButton
					variant="secondary"
					size="sm"
					icon={ZoomIn}
					label={t('operate.decisionInstance.ds.drd.zoomIn')}
					tooltipSide="right"
					onClick={() => viewerRef.current?.zoomIn()}
				/>
				<IconButton
					variant="secondary"
					size="sm"
					icon={ZoomOut}
					label={t('operate.decisionInstance.ds.drd.zoomOut')}
					tooltipSide="right"
					onClick={() => viewerRef.current?.zoomOut()}
				/>
				<IconButton
					variant="secondary"
					size="sm"
					icon={Scan}
					label={t('operate.decisionInstance.ds.drd.resetZoom')}
					tooltipSide="right"
					onClick={() => viewerRef.current?.fit()}
				/>
			</div>
			{overlays.map(({decisionDefinitionId, state, container}) => (
				<DecisionStateBadge
					key={decisionDefinitionId}
					state={state}
					container={container}
					title={`${decisionDefinitionId}: ${
						state === 'EVALUATED'
							? t('operate.decisions.filters.evaluated')
							: state === 'FAILED'
								? t('operate.decisions.filters.failed')
								: state
					}`}
				/>
			))}
		</div>
	);
}

export {DrdViewer};
