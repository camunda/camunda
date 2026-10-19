/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C4Provider} from '@camunda/design-system';
import {QueryClientProvider, type QueryClient} from '@tanstack/react-query';
import {RouterProvider} from '@tanstack/react-router';
import {observer} from 'mobx-react-lite';
import {useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {PREVIEW_SCOPE_ATTRIBUTE} from '#/tasklist/modules/theme-editor/generateCss';
import {themeEditorStore} from '#/tasklist/modules/theme-editor/themeEditorStore';
import type {PreviewRouter} from './previewRouter';
import {usePortalScope} from './usePortalScope';
import {usePreviewLock} from './usePreviewLock';

type Props = {
	router: PreviewRouter;
	queryClient: QueryClient;
};

/*
 * The form's Complete Task button sits at the end of a long form. Pinning it keeps the primary
 * button visible while the form is scrolled, so its color can be judged next to the fields.
 */
const STICKY_FORM_FOOTER_CSS = `[${PREVIEW_SCOPE_ATTRIBUTE}] [data-testid="embedded-form"] footer {
	position: sticky;
	bottom: 0;
	z-index: 1;
	background-color: var(--background);
}`;

const ThemePreview: React.FC<Props> = observer(({router, queryClient}) => {
	const {t} = useTranslation();
	const frameRef = useRef<HTMLDivElement>(null);

	usePortalScope(frameRef);
	usePreviewLock(frameRef);

	return (
		<section aria-label={t('tasklist.themeEditorPreviewLabel')} className="relative h-full min-h-0 min-w-0">
			<style>{`${STICKY_FORM_FOOTER_CSS}\n${themeEditorStore.previewCss}`}</style>
			<div
				ref={frameRef}
				{...{[PREVIEW_SCOPE_ATTRIBUTE]: ''}}
				className={cn(
					'h-full overflow-hidden rounded-xl border border-border [contain:layout_paint]',
					// The dark wrapper and the sidebar provider are plain blocks, so pass the height down.
					'*:h-full [&_[data-slot=sidebar-provider]]:h-full',
				)}
			>
				<C4Provider>
					<QueryClientProvider client={queryClient}>
						<RouterProvider router={router} />
					</QueryClientProvider>
				</C4Provider>
			</div>
		</section>
	);
});

export {ThemePreview};
