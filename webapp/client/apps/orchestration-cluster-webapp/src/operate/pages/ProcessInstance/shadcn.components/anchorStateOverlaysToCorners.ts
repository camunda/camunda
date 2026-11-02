/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';

type StatePayload = {elementState?: string; count?: number};

const anchorStateOverlaysToCorners = (overlays: OverlayData[]): OverlayData[] =>
	overlays.map((overlay) => {
		const {elementState, count} = (overlay.payload ?? {}) as StatePayload;
		if (overlay.type !== 'instance-state' || count === undefined || elementState === 'completedEndEvents') {
			return overlay;
		}
		const {position} = overlay;
		return {
			...overlay,
			position: {
				...('bottom' in position ? {bottom: 0} : {top: 0}),
				...('right' in position ? {right: 0} : {left: 0}),
			},
		};
	});

export {anchorStateOverlaysToCorners};
