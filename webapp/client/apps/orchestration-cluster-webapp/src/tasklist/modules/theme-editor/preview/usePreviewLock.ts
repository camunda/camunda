/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {PREVIEW_TASKS} from './fixtures';

const OPEN_TASK_PATH = `/tasklist/${PREVIEW_TASKS[0]!.userTaskKey}`;
const TASK_CARD_PATH = /^\/tasklist\/\d+/;

/**
 * The preview only needs to show how the task list looks, so only the open task, the first card,
 * keeps working. The other cards and the auto-select switch are made inert: no clicks, no focus.
 * The list is virtualized and re-renders as it scrolls, so the preview is watched for new nodes.
 */
function usePreviewLock(previewRef: React.RefObject<HTMLElement | null>) {
	useEffect(() => {
		const preview = previewRef.current;

		if (preview === null) {
			return;
		}

		const lock = () => {
			const otherTaskCards = Array.from(preview.querySelectorAll<HTMLAnchorElement>('a[href]')).filter((link) => {
				const path = link.getAttribute('href') ?? '';

				return TASK_CARD_PATH.test(path) && !path.startsWith(OPEN_TASK_PATH);
			});
			const autoSelectSwitch = preview.querySelector('[role="switch"]')?.closest('section') ?? null;

			for (const element of [...otherTaskCards, ...(autoSelectSwitch === null ? [] : [autoSelectSwitch])]) {
				if (!element.hasAttribute('inert')) {
					element.setAttribute('inert', '');
				}
			}
		};

		const observer = new MutationObserver(lock);
		observer.observe(preview, {childList: true, subtree: true});
		lock();

		return () => observer.disconnect();
	}, [previewRef]);
}

export {usePreviewLock};
