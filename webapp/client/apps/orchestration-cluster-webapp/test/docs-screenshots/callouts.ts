/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference lib="dom" />

import type {Locator, Page} from '@playwright/test';

type Side = 'top' | 'bottom' | 'left' | 'right';
type Box = {x: number; y: number; width: number; height: number};

type Callout = {
	label: string;
	target: Locator | Locator[];
	side: Side;
	distance?: number;
	offset?: number;
	outline?: boolean;
	fitText?: boolean;
};

type ResolvedCallout = Omit<Callout, 'target' | 'fitText'> & {targets: Box[]};

const APP_SELECTOR = '#app';
const ANNOTATIONS_ID = 'docs-screenshot-annotations';

async function getBox(locator: Locator, {fitText = false}: {fitText?: boolean} = {}): Promise<Box> {
	if (fitText) {
		return locator.evaluate((element) => {
			const range = document.createRange();
			range.selectNodeContents(element);
			const {x, y, width, height} = range.getBoundingClientRect();

			return {x, y, width, height};
		});
	}

	const box = await locator.boundingBox();

	if (box === null) {
		throw new Error(`Cannot annotate an element that is not visible: ${locator.toString()}`);
	}

	return box;
}

async function transformApp(page: Page, {scale, x = 0, y = 0}: {scale: number; x?: number; y?: number}) {
	await page.addStyleTag({
		content: `
			html, body { background: #fff !important; overflow: hidden !important; }
			${APP_SELECTOR} {
				transform: translate(${x}px, ${y}px) scale(${scale});
				transform-origin: top left;
				box-shadow: 0 0 0 1px #d9d9d9;
			}
		`,
	});
}

async function frameApp(page: Page, {scale}: {scale: number}) {
	const viewport = page.viewportSize();

	if (viewport === null) {
		throw new Error('Docs screenshots require a browser viewport');
	}

	await transformApp(page, {
		scale,
		x: (viewport.width * (1 - scale)) / 2,
		y: (viewport.height * (1 - scale)) / 2,
	});
}

async function isolateElements(page: Page, locators: Locator[]) {
	for (const locator of locators) {
		await locator.evaluate((element) => element.setAttribute('data-docs-isolated', ''));
	}

	await page.addStyleTag({
		content: `
			${APP_SELECTOR} { visibility: hidden !important; box-shadow: none !important; }
			[data-docs-isolated], [data-docs-isolated] * { visibility: visible !important; }
		`,
	});
}

async function addCallouts(page: Page, callouts: Callout[]): Promise<Box> {
	const resolved: ResolvedCallout[] = [];

	for (const {target, fitText, ...callout} of callouts) {
		const targets = await Promise.all(
			(Array.isArray(target) ? target : [target]).map((locator) => getBox(locator, {fitText})),
		);
		resolved.push({...callout, targets});
	}

	return page.evaluate(
		({callouts, id}) => {
			const COLOR = '#000';
			const LINE_WIDTH = 2;
			const DOT_RADIUS = 4;
			const DEFAULT_DISTANCE = 48;
			const SVG_NS = 'http://www.w3.org/2000/svg';

			document.getElementById(id)?.remove();

			const container = document.createElement('div');
			container.id = id;
			Object.assign(container.style, {
				position: 'fixed',
				inset: '0',
				zIndex: '2147483647',
				pointerEvents: 'none',
			});

			const svg = document.createElementNS(SVG_NS, 'svg');
			svg.setAttribute('width', String(window.innerWidth));
			svg.setAttribute('height', String(window.innerHeight));
			Object.assign(svg.style, {position: 'absolute', inset: '0', overflow: 'visible'});
			container.append(svg);
			document.body.append(container);

			const bounds = {left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity};
			const extend = (left: number, top: number, right: number, bottom: number) => {
				bounds.left = Math.min(bounds.left, left);
				bounds.top = Math.min(bounds.top, top);
				bounds.right = Math.max(bounds.right, right);
				bounds.bottom = Math.max(bounds.bottom, bottom);
			};

			const drawPath = (points: Array<[number, number]>) => {
				const path = document.createElementNS(SVG_NS, 'path');
				path.setAttribute('d', points.map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${x} ${y}`).join(' '));
				path.setAttribute('fill', 'none');
				path.setAttribute('stroke', COLOR);
				path.setAttribute('stroke-width', String(LINE_WIDTH));
				svg.append(path);
			};

			const drawDot = (x: number, y: number) => {
				const dot = document.createElementNS(SVG_NS, 'circle');
				dot.setAttribute('cx', String(x));
				dot.setAttribute('cy', String(y));
				dot.setAttribute('r', String(DOT_RADIUS));
				dot.setAttribute('fill', COLOR);
				svg.append(dot);
				extend(x - DOT_RADIUS, y - DOT_RADIUS, x + DOT_RADIUS, y + DOT_RADIUS);
			};

			const drawOutline = ({x, y, width, height}: {x: number; y: number; width: number; height: number}) => {
				const rect = document.createElementNS(SVG_NS, 'rect');
				rect.setAttribute('x', String(x));
				rect.setAttribute('y', String(y));
				rect.setAttribute('width', String(width));
				rect.setAttribute('height', String(height));
				rect.setAttribute('fill', 'none');
				rect.setAttribute('stroke', COLOR);
				rect.setAttribute('stroke-width', String(LINE_WIDTH));
				svg.append(rect);
				extend(x - LINE_WIDTH, y - LINE_WIDTH, x + width + LINE_WIDTH, y + height + LINE_WIDTH);
			};

			const createLabel = (text: string) => {
				const label = document.createElement('div');
				label.textContent = text;
				Object.assign(label.style, {
					position: 'absolute',
					left: '0',
					top: '0',
					padding: '11px 20px',
					borderRadius: '10px',
					background: COLOR,
					color: '#fff',
					fontFamily: getComputedStyle(document.body).fontFamily,
					fontSize: '18px',
					fontWeight: '600',
					lineHeight: '1',
					letterSpacing: '0.01em',
					whiteSpace: 'nowrap',
				});
				container.append(label);
				const {width, height} = label.getBoundingClientRect();

				return {
					width,
					height,
					place: (left: number, top: number) => {
						label.style.left = `${left}px`;
						label.style.top = `${top}px`;
						extend(left, top, left + width, top + height);
					},
				};
			};

			for (const {label: text, targets, side, distance = DEFAULT_DISTANCE, offset = 0, outline = false} of callouts) {
				const label = createLabel(text);

				for (const target of targets) {
					extend(target.x, target.y, target.x + target.width, target.y + target.height);

					if (outline) {
						drawOutline(target);
					}
				}

				const anchors = targets.map(({x, y, width, height}): [number, number] => {
					switch (side) {
						case 'left':
							return [x, y + height / 2];
						case 'right':
							return [x + width, y + height / 2];
						case 'top':
							return [x + width / 2, y];
						case 'bottom':
						default:
							return [x + width / 2, y + height];
					}
				});

				if (anchors.length > 1) {
					if (side === 'top' || side === 'bottom') {
						throw new Error('Callouts with multiple targets only support the left and right sides');
					}

					const direction = side === 'right' ? 1 : -1;
					const edges = anchors.map(([x]) => x);
					const outermost = side === 'right' ? Math.max(...edges) : Math.min(...edges);
					const bracketX = outermost + (direction * distance) / 2;
					const ys = anchors.map(([, y]) => y);
					const centerY = (Math.min(...ys) + Math.max(...ys)) / 2 + offset;
					const labelEdgeX = bracketX + (direction * distance) / 2;

					for (const [x, y] of anchors) {
						drawPath([
							[x, y],
							[bracketX, y],
						]);
						drawDot(x, y);
					}

					drawPath([
						[bracketX, Math.min(...ys, centerY)],
						[bracketX, Math.max(...ys, centerY)],
					]);
					drawPath([
						[bracketX, centerY],
						[labelEdgeX, centerY],
					]);
					label.place(side === 'right' ? labelEdgeX : labelEdgeX - label.width, centerY - label.height / 2);
					continue;
				}

				const [anchor] = anchors;

				if (anchor === undefined) {
					throw new Error(`Callout "${text}" has no target`);
				}

				const [anchorX, anchorY] = anchor;

				switch (side) {
					case 'left':
					case 'right': {
						const labelEdgeX = side === 'left' ? anchorX - distance : anchorX + distance;
						const centerY = anchorY + offset;
						const middleX = (anchorX + labelEdgeX) / 2;

						drawPath([
							[anchorX, anchorY],
							[middleX, anchorY],
							[middleX, centerY],
							[labelEdgeX, centerY],
						]);
						label.place(side === 'left' ? labelEdgeX - label.width : labelEdgeX, centerY - label.height / 2);
						break;
					}
					case 'top':
					case 'bottom':
					default: {
						const labelEdgeY = side === 'top' ? anchorY - distance : anchorY + distance;
						const centerX = anchorX + offset;
						const middleY = (anchorY + labelEdgeY) / 2;

						drawPath([
							[anchorX, anchorY],
							[anchorX, middleY],
							[centerX, middleY],
							[centerX, labelEdgeY],
						]);
						label.place(centerX - label.width / 2, side === 'top' ? labelEdgeY - label.height : labelEdgeY);
						break;
					}
				}

				drawDot(anchorX, anchorY);
			}

			return {
				x: bounds.left,
				y: bounds.top,
				width: bounds.right - bounds.left,
				height: bounds.bottom - bounds.top,
			};
		},
		{callouts: resolved, id: ANNOTATIONS_ID},
	);
}

function padBox(page: Page, {x, y, width, height}: Box, padding: number): Box {
	const viewport = page.viewportSize();
	const left = Math.max(0, x - padding);
	const top = Math.max(0, y - padding);
	const right = Math.min(viewport?.width ?? Infinity, x + width + padding);
	const bottom = Math.min(viewport?.height ?? Infinity, y + height + padding);

	return {x: left, y: top, width: right - left, height: bottom - top};
}

export {addCallouts, frameApp, isolateElements, padBox, transformApp, getBox};
