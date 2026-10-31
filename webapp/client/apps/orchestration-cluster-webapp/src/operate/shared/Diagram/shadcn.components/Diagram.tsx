/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef, useEffect, useLayoutEffect, useState, useCallback, useId} from 'react';
import {useTranslation} from 'react-i18next';
import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
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
	ariaLabel?: string;
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

const ARROW_DIRECTIONS: Partial<Record<string, [number, number]>> = {
	ArrowRight: [1, 0],
	ArrowLeft: [-1, 0],
	ArrowDown: [0, 1],
	ArrowUp: [0, -1],
};

function findNearestInDirection(from: SVGElement, candidates: SVGElement[], [dx, dy]: [number, number]) {
	const center = (element: Element) => {
		const {left, top, width, height} = element.getBoundingClientRect();
		return {x: left + width / 2, y: top + height / 2};
	};
	const origin = center(from);
	let best: SVGElement | undefined;
	let bestScore = Infinity;

	candidates.forEach((candidate) => {
		if (candidate === from) {
			return;
		}
		const {x, y} = center(candidate);
		const along = (x - origin.x) * dx + (y - origin.y) * dy;
		const across = Math.abs((x - origin.x) * dy) + Math.abs((y - origin.y) * dx);
		if (along <= 0 || across > along * 2) {
			return;
		}
		const score = along + across * 2;
		if (score < bestScore) {
			best = candidate;
			bestScore = score;
		}
	});

	return best;
}

function getElementTypeKey(type: string | undefined) {
	const name = type?.replace(/^bpmn:/, '');
	return name === undefined ? undefined : name.charAt(0).toLowerCase() + name.slice(1);
}

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
	ariaLabel,
}: Props) {
	const {t} = useTranslation();
	const keyboardHintId = useId();
	const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
	const diagramRef = useRef<HTMLDivElement | null>(null);
	const [isDiagramRendered, setIsDiagramRendered] = useState(false);
	const viewerRef = useRef<BpmnJS | null>(null);
	const [isViewboxChanging, setIsViewboxChanging] = useState(false);
	const [isKeyboardFocused, setIsKeyboardFocused] = useState(false);
	const [tooltipAnchor, setTooltipAnchor] = useState<{x: number; y: number} | null>(null);
	const [activeOverlays, setActiveOverlays] = useState<OverlayEntry[]>([]);
	const [selectedElement, setSelectedElement] = useState<SVGGraphicsElement | undefined>(undefined);

	const {isFullscreen, handleFullscreen} = useFullscreen(diagramRef, viewerRef);
	const {isMinimapOpen, handleMinimapToggle, resetMinimap} = useMinimap(diagramCanvasRef, viewerRef, isDiagramRendered);

	const hasUserAdjustedViewRef = useRef(false);

	const handleZoomIn = useCallback(() => {
		hasUserAdjustedViewRef.current = true;
		viewerRef.current?.zoomIn();
	}, []);
	const handleZoomOut = useCallback(() => {
		hasUserAdjustedViewRef.current = true;
		viewerRef.current?.zoomOut();
	}, []);
	const handleZoomReset = useCallback(() => {
		hasUserAdjustedViewRef.current = false;
		viewerRef.current?.zoomReset();
	}, []);

	useEffect(() => {
		const container = diagramCanvasRef.current;
		if (container === null || typeof ResizeObserver === 'undefined') {
			return;
		}

		let hasObservedInitialSize = false;
		let frame = 0;
		const observer = new ResizeObserver(() => {
			if (!hasObservedInitialSize) {
				hasObservedInitialSize = true;
				return;
			}
			cancelAnimationFrame(frame);
			frame = requestAnimationFrame(() => {
				if (hasUserAdjustedViewRef.current) {
					viewerRef.current?.notifyResize();
				} else {
					viewerRef.current?.zoomReset();
				}
			});
		});
		observer.observe(container);

		const markAdjusted = () => {
			hasUserAdjustedViewRef.current = true;
		};
		const markDragged = (event: PointerEvent) => {
			if (event.buttons > 0) {
				markAdjusted();
			}
		};
		container.addEventListener('wheel', markAdjusted, {passive: true});
		container.addEventListener('pointermove', markDragged);

		return () => {
			container.removeEventListener('wheel', markAdjusted);
			container.removeEventListener('pointermove', markDragged);
			cancelAnimationFrame(frame);
			observer.disconnect();
		};
	}, []);

	useLayoutEffect(() => {
		let mounted = true;

		async function renderDiagram() {
			if (!diagramCanvasRef.current) {
				return;
			}

			if (viewerRef.current === null) {
				viewerRef.current = new BpmnJS({shouldResetStaleSequenceFlows: true});
			}
			const viewer = viewerRef.current;

			viewer.onOverlayChange = setActiveOverlays;
			setIsDiagramRendered(false);

			await viewer.render({
				container: diagramCanvasRef.current,
				xml,
				selectableElements,
				selectedElementIds,
				overlaysData,
				highlightedSequenceFlows,
				highlightedElementIds,
				hasOuterBorderOnSelection,
				customElementClasses,
			});

			if (mounted) {
				setSelectedElement(viewer.selectedElement);
				setIsDiagramRendered(true);
				resetMinimap();
			}
		}
		renderDiagram();

		return () => {
			mounted = false;
		};
	}, [
		xml,
		selectableElements,
		selectedElementIds,
		overlaysData,
		highlightedSequenceFlows,
		highlightedElementIds,
		hasOuterBorderOnSelection,
		resetMinimap,
		customElementClasses,
	]);

	useEffect(() => {
		if (!isDiagramRendered || diagramCanvasRef.current === null) {
			return;
		}

		const canvas = diagramCanvasRef.current;
		canvas.querySelector('svg')?.setAttribute('tabindex', '-1');

		const getLabel = (element: Element) => {
			const elementId = element.getAttribute('data-element-id') ?? '';
			const name = viewerRef.current?.getElementName(elementId)?.trim();
			const typeKey = getElementTypeKey(viewerRef.current?.getElementType(elementId));
			const type =
				typeKey === undefined
					? undefined
					: t(`operate.shared.diagram.elementTypes.${typeKey}`, {
							defaultValue: typeKey.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase(),
						});
			if (name && type) {
				return t('operate.shared.diagram.elementLabel', {name, type});
			}
			return name || type || '';
		};

		canvas.querySelectorAll('[role="button"]:not(.op-selectable)').forEach((element) => {
			['tabindex', 'role', 'aria-pressed', 'aria-label', 'aria-describedby'].forEach((attribute) =>
				element.removeAttribute(attribute),
			);
		});

		const selectable = [...canvas.querySelectorAll<SVGElement>('.op-selectable')];
		const tabStop = selectable.find((element) => element.classList.contains('op-selected')) ?? selectable[0];
		selectable.forEach((element) => {
			element.setAttribute('tabindex', element === tabStop ? '0' : '-1');
			element.setAttribute('role', 'button');
			element.setAttribute('aria-pressed', String(element.classList.contains('op-selected')));
			element.setAttribute('aria-label', getLabel(element));
			element.setAttribute('aria-describedby', keyboardHintId);
		});

		canvas.querySelectorAll('.op-non-selectable').forEach((element) => {
			const label = getLabel(element);
			element.setAttribute('role', 'img');
			element.setAttribute(
				'aria-label',
				nonSelectableNodeTooltipText === undefined ? label : `${label}, ${nonSelectableNodeTooltipText}`,
			);
		});
	}, [
		isDiagramRendered,
		selectableElements,
		selectedElementIds,
		customElementClasses,
		nonSelectableNodeTooltipText,
		keyboardHintId,
		t,
	]);

	const handleCanvasKeyDown = useCallback((event: React.KeyboardEvent<HTMLDivElement>) => {
		const target = event.target instanceof Element ? event.target.closest<SVGElement>('.op-selectable') : null;
		if (target === null) {
			return;
		}

		if (event.key === 'Enter' || event.key === ' ') {
			event.preventDefault();
			target.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}));
			return;
		}

		const direction = ARROW_DIRECTIONS[event.key];
		if (direction === undefined) {
			return;
		}
		const next = findNearestInDirection(
			target,
			[...event.currentTarget.querySelectorAll<SVGElement>('.op-selectable')],
			direction,
		);
		if (next !== undefined) {
			event.preventDefault();
			next.focus();
		}
	}, []);

	const handleCanvasFocus = useCallback((event: React.FocusEvent<HTMLDivElement>) => {
		setIsKeyboardFocused(event.target instanceof Element && event.target.matches(':focus-visible'));
		const target = event.target instanceof Element ? event.target.closest('.op-selectable') : null;
		if (target === null) {
			return;
		}
		event.currentTarget.querySelectorAll('.op-selectable').forEach((element) => {
			element.setAttribute('tabindex', element === target ? '0' : '-1');
		});
		const elementId = target.getAttribute('data-element-id');
		if (elementId !== null) {
			viewerRef.current?.ensureOutline(elementId);
		}
	}, []);

	const handleCanvasPointerOver = useCallback(
		(event: React.PointerEvent<HTMLDivElement>) => {
			const isNonSelectable = event.target instanceof Element && event.target.closest('.op-non-selectable') !== null;
			const container = diagramRef.current;
			if (!isNonSelectable || container === null || nonSelectableNodeTooltipText === undefined) {
				setTooltipAnchor(null);
				return;
			}
			const rect = container.getBoundingClientRect();
			setTooltipAnchor({x: event.clientX - rect.left, y: event.clientY - rect.top});
		},
		[nonSelectableNodeTooltipText],
	);

	useLayoutEffect(() => {
		const viewer = viewerRef.current;
		if (!viewer) {
			return;
		}

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
	}, [onElementSelection, onRootChange, onElementDoubleClick]);

	useEffect(() => {
		return () => {
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
					aria-label={ariaLabel ?? t('operate.shared.diagram.label')}
					aria-describedby={keyboardHintId}
					onKeyDown={handleCanvasKeyDown}
					onFocus={handleCanvasFocus}
					onBlur={() => setIsKeyboardFocused(false)}
					onPointerOver={handleCanvasPointerOver}
					onPointerLeave={() => setTooltipAnchor(null)}
					className="absolute top-0 left-0 h-full min-h-[200px] w-full"
				/>
				<span
					id={keyboardHintId}
					className={cn(
						isKeyboardFocused
							? 'bg-neutral-background-default text-neutral-foreground-subtle border-neutral-border-subtle pointer-events-none absolute bottom-4 left-4 z-10 rounded-sm border px-2 py-1 text-xs'
							: 'sr-only',
					)}
				>
					{t('operate.shared.diagram.keyboardHint')}
				</span>
				{nonSelectableNodeTooltipText !== undefined && (
					<Tooltip
						open={tooltipAnchor !== null}
						onOpenChange={(open) => {
							if (!open) {
								setTooltipAnchor(null);
							}
						}}
					>
						<TooltipTrigger asChild>
							<span
								aria-hidden
								className="pointer-events-none absolute size-0"
								style={{left: tooltipAnchor?.x ?? 0, top: tooltipAnchor?.y ?? 0}}
							/>
						</TooltipTrigger>
						<TooltipContent side="top">{nonSelectableNodeTooltipText}</TooltipContent>
					</Tooltip>
				)}
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
