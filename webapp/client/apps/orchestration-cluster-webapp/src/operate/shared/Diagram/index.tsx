/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef, useEffect, useLayoutEffect, useState, useCallback} from 'react';
import {BpmnJS, type OnElementSelection, type OnElementDoubleClick} from './BpmnJS';
import type {OverlayData, OverlayEntry} from './overlayTypes';
import {DiagramControls} from './DiagramControls';
import {DiagramContainer, DiagramCanvas} from './styled';
import {DiagramOverlayContext} from './DiagramOverlayContext';

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

		el.requestFullscreen().catch(() => {
			// Fullscreen blocked by Permissions Policy — silently ignore
		});
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
		setIsMinimapOpen(false);
	}, []);

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
}: Props) {
	const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
	const diagramRef = useRef<HTMLDivElement | null>(null);
	const [isDiagramRendered, setIsDiagramRendered] = useState(false);
	const viewerRef = useRef<BpmnJS | null>(null);
	const [isViewboxChanging, setIsViewboxChanging] = useState(false);
	const [activeOverlays, setActiveOverlays] = useState<OverlayEntry[]>([]);
	const [selectedElement, setSelectedElement] = useState<SVGGraphicsElement | undefined>(undefined);

	const {isFullscreen, handleFullscreen} = useFullscreen(diagramRef, viewerRef);
	const {isMinimapOpen, handleMinimapToggle, resetMinimap} = useMinimap(diagramCanvasRef, viewerRef, isDiagramRendered);

	const handleZoomIn = useCallback(() => viewerRef.current?.zoomIn(), []);
	const handleZoomOut = useCallback(() => viewerRef.current?.zoomOut(), []);
	const handleZoomReset = useCallback(() => viewerRef.current?.zoomReset(), []);

	useLayoutEffect(() => {
		async function renderDiagram() {
			if (!diagramCanvasRef.current) {
				return;
			}

			if (viewerRef.current === null) {
				viewerRef.current = new BpmnJS();
			}
			const viewer = viewerRef.current;

			viewer.onOverlayChange = setActiveOverlays;
			setIsDiagramRendered(false);
			setSelectedElement(undefined);

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

			setIsDiagramRendered(true);
			resetMinimap();
		}
		renderDiagram();
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
	]);

	useEffect(() => {
		const viewer = viewerRef.current;
		if (!viewer) {
			return;
		}

		if (onElementSelection !== undefined) {
			viewer.onElementSelection = (elementId, isMultiInstance) => {
				setSelectedElement(viewer.selectedElement);
				onElementSelection(elementId, isMultiInstance);
			};
			viewer.onViewboxChange = setIsViewboxChanging;

			viewer.onRootChange = (rootElementId) => {
				const getSelectionRootId = (elementId: string) => viewer.findRootId(elementId);
				onRootChange?.(rootElementId, getSelectionRootId);
			};
		}

		viewer.onElementDoubleClick = onElementDoubleClick;
	}, [onElementSelection, onRootChange, onElementDoubleClick]);

	useEffect(() => {
		return () => {
			viewerRef.current?.reset();
		};
	}, []);

	return (
		<DiagramOverlayContext.Provider value={activeOverlays}>
			<DiagramContainer data-testid="diagram" ref={diagramRef} $isFullscreen={isFullscreen}>
				<DiagramCanvas ref={diagramCanvasRef} data-testid="diagram-canvas" />
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
						/>
						{children}
					</>
				)}
				{!isViewboxChanging && React.isValidElement(selectedElementOverlay)
					? React.cloneElement(selectedElementOverlay, {
							selectedElementRef: selectedElement,
							diagramCanvasRef,
						})
					: null}
			</DiagramContainer>
		</DiagramOverlayContext.Provider>
	);
}

export {Diagram};
