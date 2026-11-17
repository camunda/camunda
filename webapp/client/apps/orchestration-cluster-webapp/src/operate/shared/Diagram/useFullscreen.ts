/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import type {BpmnJS} from './BpmnJS';

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
			document.exitFullscreen().catch(() => undefined);
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

export {useFullscreen};
