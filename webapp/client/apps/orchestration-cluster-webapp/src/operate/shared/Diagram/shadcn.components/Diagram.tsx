/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef, useEffect, useLayoutEffect, useState, useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {BpmnJS, type OnElementSelection, type OnElementDoubleClick} from '../BpmnJS';
import type {OverlayData, OverlayEntry} from '../overlayTypes';
import {DiagramControls} from './DiagramControls';
import {DiagramOverlayContext} from '../DiagramOverlayContext';

type OnRootChange = (rootElementId: string, getSelectionRootId: (elementId: string) => string | undefined) => void;

type SelectedElementOverlayProps = {
	selectedElementRef: SVGElement;
	diagramCanvasRef: React.Ref<Element>;
};

type Props = {
	xml: string;
	selectableElements?: string[];
	selectedElementIds?: string[];
	onElementSelection?: OnElementSelection;
	onRootChange?: OnRootChange;
	overlaysData?: OverlayData[];
	children?: React.ReactNode;
	selectedElementOverlay?: React.ReactElement<SelectedElementOverlayProps> | false;
	highlightedSequenceFlows?: string[];
	highlightedElementIds?: string[];
	nonSelectableNodeTooltipText?: string;
	hasOuterBorderOnSelection?: boolean;
	onElementDoubleClick?: OnElementDoubleClick;
	customElementClasses?: [elementId: string, className: string][];
	download?: {xml: string; filename: string};
	onRenderError?: (error: unknown) => void;
};

const useFullscreen = (
	diagramRef: React.RefObject<HTMLDivElement | null>,
	viewerRef: React.RefObject<BpmnJS | null>,
) => {
	const [isFullscreen, setIsFullscreen] = useState(false);

	const handleFullscreen = useCallback(() => {
		const el = diagramRef.current;
		if (el === null) {
			return;
		}

		if (document.fullscreenElement) {
			document.exitFullscreen();
			return;
		}

		el.requestFullscreen().catch(() => undefined);
	}, [diagramRef]);

	useEffect(() => {
		const handleFullscreenChange = () => {
			setIsFullscreen(document.fullscreenElement === diagramRef.current);

			requestAnimationFrame(() => {
				viewerRef.current?.notifyResize();
				viewerRef.current?.zoomReset();
			});
		};

		document.addEventListener('fullscreenchange', handleFullscreenChange);

		return () => {
			document.removeEventListener('fullscreenchange', handleFullscreenChange);
		};
	}, [diagramRef, viewerRef]);

	return {isFullscreen, handleFullscreen};
};

const useMinimap = (
	diagramCanvasRef: React.RefObject<HTMLDivElement | null>,
	viewerRef: React.RefObject<BpmnJS | null>,
	isDiagramRendered: boolean,
) => {
	const [isMinimapOpen, setIsMinimapOpen] = useState(false);

	const resetMinimap = useCallback(() => {
		setIsMinimapOpen(viewerRef.current?.isMinimapOpen() ?? false);
	}, [viewerRef]);

	useEffect(() => {
		if (isDiagramRendered && diagramCanvasRef.current) {
			const minimap = diagramCanvasRef.current.querySelector('.djs-minimap');
			if (minimap) {
				minimap.setAttribute('aria-hidden', 'true');
			}
		}
	}, [isDiagramRendered, diagramCanvasRef]);

	const handleMinimapToggle = useCallback(() => {
		viewerRef.current?.toggleMinimap();
		setIsMinimapOpen(viewerRef.current?.isMinimapOpen() ?? false);
	}, [viewerRef]);

	return {isMinimapOpen, handleMinimapToggle, resetMinimap};
};

function Diagram({
	xml,
	selectableElements,
	selectedElementIds,
	onElementSelection,
	onRootChange,
	overlaysData,
	selectedElementOverlay,
	children,
	highlightedSequenceFlows,
	highlightedElementIds,
	nonSelectableNodeTooltipText,
	hasOuterBorderOnSelection = true,
	onElementDoubleClick,
	customElementClasses,
	download,
	onRenderError,
}: Props) {
	const {t} = useTranslation();
	const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
	const diagramRef = useRef<HTMLDivElement | null>(null);
	const [isDiagramRendered, setIsDiagramRendered] = useState(false);
	const viewerRef = useRef<BpmnJS | null>(null);
	const renderRunRef = useRef(0);
	const onRenderErrorRef = useRef(onRenderError);
	const handlersRef = useRef({onElementSelection, onRootChange, onElementDoubleClick});
	const renderedXmlRef = useRef<string | null>(null);
	const renderQueueRef = useRef<Promise<void>>(Promise.resolve());
	const [isViewboxChanging, setIsViewboxChanging] = useState(false);
	const [activeOverlays, setActiveOverlays] = useState<OverlayEntry[]>([]);
	const [selectedElement, setSelectedElement] = useState<SVGGraphicsElement | undefined>(undefined);

	const {isFullscreen, handleFullscreen} = useFullscreen(diagramRef, viewerRef);
	const {isMinimapOpen, handleMinimapToggle, resetMinimap} = useMinimap(diagramCanvasRef, viewerRef, isDiagramRendered);

	const handleZoomIn = useCallback(() => viewerRef.current?.zoomIn(), []);
	const handleZoomOut = useCallback(() => viewerRef.current?.zoomOut(), []);
	const handleZoomReset = useCallback(() => viewerRef.current?.zoomReset(), []);

	const bindHandlers = useCallback((viewer: BpmnJS) => {
		const {onElementSelection, onRootChange, onElementDoubleClick} = handlersRef.current;

		viewer.onElementSelection = undefined;
		viewer.onViewboxChange = undefined;
		viewer.onRootChange = undefined;

		if (onElementSelection !== undefined) {
			viewer.onElementSelection = (elementId, isMultiInstance, clickedElementId) => {
				setSelectedElement(viewer.selectedElement);
				if (clickedElementId === undefined) {
					onElementSelection(elementId, isMultiInstance);
				} else {
					onElementSelection(elementId, isMultiInstance, clickedElementId);
				}
			};
			viewer.onViewboxChange = setIsViewboxChanging;
			viewer.onRootChange = (rootElementId) => {
				const getSelectionRootId = (elementId: string) => viewer.findRootId(elementId);
				onRootChange?.(rootElementId, getSelectionRootId);
			};
		}

		viewer.onElementDoubleClick = onElementDoubleClick;
	}, []);

	const acquireViewer = useCallback(() => {
		if (viewerRef.current === null) {
			const viewer = new BpmnJS();
			viewer.onOverlayChange = setActiveOverlays;
			bindHandlers(viewer);
			viewerRef.current = viewer;
		}

		return viewerRef.current;
	}, [bindHandlers]);

	useLayoutEffect(() => {
		onRenderErrorRef.current = onRenderError;
	});

	useLayoutEffect(() => {
		renderRunRef.current += 1;
		const run = renderRunRef.current;
		if (renderedXmlRef.current !== xml) {
			renderedXmlRef.current = xml;
			setIsDiagramRendered(false);
		}

		acquireViewer();

		// Renders are queued per viewer so a render started while an XML import is pending
		// waits for it and the latest props are the ones applied last.
		const render = renderQueueRef.current.then(async () => {
			if (run !== renderRunRef.current || !diagramCanvasRef.current) {
				return;
			}

			const viewer = acquireViewer();

			try {
				await viewer.render({
					container: diagramCanvasRef.current,
					xml,
					selectableElements,
					selectedElementIds,
					overlaysData,
					highlightedSequenceFlows,
					highlightedElementIds,
					nonSelectableNodeTooltipText,
					hasOuterBorderOnSelection,
					customElementClasses,
				});
			} catch (error) {
				// A failed import leaves the viewer holding the broken XML, so every render after it needs a fresh viewer.
				viewer.reset();
				if (viewerRef.current === viewer) {
					viewerRef.current = null;
				}
				if (run === renderRunRef.current) {
					onRenderErrorRef.current?.(error);
				}
				return;
			}

			if (run === renderRunRef.current) {
				setSelectedElement(viewer.selectedElement);
				setIsDiagramRendered(true);
				resetMinimap();
			}
		});
		renderQueueRef.current = render.catch(() => undefined);
	}, [
		xml,
		selectableElements,
		selectedElementIds,
		overlaysData,
		highlightedSequenceFlows,
		highlightedElementIds,
		nonSelectableNodeTooltipText,
		hasOuterBorderOnSelection,
		resetMinimap,
		customElementClasses,
		acquireViewer,
	]);

	useLayoutEffect(() => {
		handlersRef.current = {onElementSelection, onRootChange, onElementDoubleClick};
		if (viewerRef.current !== null) {
			bindHandlers(viewerRef.current);
		}
	});

	useEffect(() => {
		return () => {
			renderRunRef.current += 1;
			viewerRef.current?.reset();
		};
	}, []);

	return (
		<DiagramOverlayContext.Provider value={activeOverlays}>
			<div
				data-testid="diagram"
				ref={diagramRef}
				className={cn('bpmn-viewer-theme relative h-full w-full', isFullscreen && 'bg-neutral-background-subtle')}
			>
				<div
					ref={diagramCanvasRef}
					data-testid="diagram-canvas"
					role="region"
					aria-label={t('operate.shared.diagram.label')}
					className="absolute top-0 left-0 h-full min-h-[200px] w-full"
				/>
				{isDiagramRendered && (
					<>
						<DiagramControls
							handleZoomIn={handleZoomIn}
							handleZoomOut={handleZoomOut}
							handleZoomReset={handleZoomReset}
							handleFullscreen={handleFullscreen}
							isFullscreen={isFullscreen}
							handleMinimapToggle={handleMinimapToggle}
							isMinimapOpen={isMinimapOpen}
							download={download}
						/>
						{children}
					</>
				)}
				{!isViewboxChanging && React.isValidElement(selectedElementOverlay) && selectedElement !== undefined
					? React.cloneElement(selectedElementOverlay, {
							selectedElementRef: selectedElement,
							diagramCanvasRef,
						})
					: null}
			</div>
		</DiagramOverlayContext.Provider>
	);
}

export {Diagram};
