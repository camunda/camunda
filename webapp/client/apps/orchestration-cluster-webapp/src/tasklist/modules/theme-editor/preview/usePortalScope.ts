/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {PREVIEW_SCOPE_ATTRIBUTE} from '#/tasklist/modules/theme-editor/generateCss';

const REFERENCE_ATTRIBUTES = ['aria-controls', 'aria-describedby', 'aria-owns'] as const;
const LABEL_ATTRIBUTES = ['aria-labelledby', 'aria-describedby'] as const;

/**
 * Design system popovers, tooltips, selects, and dialogs render in a portal at the end of
 * `<body>`, outside the preview frame, so the preview's `@scope` rule can't reach them. This hook
 * marks every portal whose trigger sits in the preview (linked through ARIA attributes such as
 * `aria-controls` or `aria-labelledby`) with the same scope attribute as the frame.
 */
function usePortalScope(previewRef: React.RefObject<HTMLElement | null>) {
	useEffect(() => {
		const preview = previewRef.current;

		if (preview === null) {
			return;
		}

		const isInScope = (element: Element) =>
			preview.contains(element) || element.closest(`[${PREVIEW_SCOPE_ATTRIBUTE}]`) !== null;

		const isReferencedFromScope = (id: string) => {
			const escapedId = CSS.escape(id);
			const selector = REFERENCE_ATTRIBUTES.map((attribute) => `[${attribute}~="${escapedId}"]`).join(',');

			return Array.from(document.querySelectorAll(selector)).some(isInScope);
		};

		const referencesScope = (element: Element) =>
			LABEL_ATTRIBUTES.some((attribute) =>
				(element.getAttribute(attribute) ?? '')
					.split(/\s+/)
					.filter((id) => id !== '')
					.some((id) => {
						const target = document.getElementById(id);
						return target !== null && isInScope(target);
					}),
			);

		const markPortals = () => {
			for (const portal of Array.from(document.body.children)) {
				if (portal.hasAttribute(PREVIEW_SCOPE_ATTRIBUTE) || portal.contains(preview)) {
					continue;
				}

				const elements = [
					portal,
					...Array.from(portal.querySelectorAll('[id], [aria-labelledby], [aria-describedby]')),
				];
				const isOpenedFromPreview = elements.some(
					(element) => (element.id !== '' && isReferencedFromScope(element.id)) || referencesScope(element),
				);

				if (isOpenedFromPreview) {
					portal.setAttribute(PREVIEW_SCOPE_ATTRIBUTE, '');
				}
			}
		};

		const observer = new MutationObserver(markPortals);
		observer.observe(document.body, {
			childList: true,
			subtree: true,
			attributes: true,
			attributeFilter: [...REFERENCE_ATTRIBUTES, ...LABEL_ATTRIBUTES],
		});
		markPortals();

		return () => observer.disconnect();
	}, [previewRef]);
}

export {usePortalScope};
