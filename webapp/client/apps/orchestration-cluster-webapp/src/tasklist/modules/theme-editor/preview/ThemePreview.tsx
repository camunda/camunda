/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, C4Provider} from '@camunda/design-system';
import {QueryClientProvider, type QueryClient} from '@tanstack/react-query';
import {RouterProvider, useRouterState} from '@tanstack/react-router';
import {observer} from 'mobx-react-lite';
import {useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {PREVIEW_SCOPE_ATTRIBUTE} from '#/tasklist/modules/theme-editor/generateCss';
import {themeEditorStore, type PreviewDataset} from '#/tasklist/modules/theme-editor/themeEditorStore';
import {PREVIEW_PATHS, type PreviewRouter} from './previewRouter';
import {usePortalScope} from './usePortalScope';

type Props = {
	router: PreviewRouter;
	queryClient: QueryClient;
};

type PreviewPage = {
	key: string;
	labelKey: string;
	path: string;
	dataset: PreviewDataset;
};

const PREVIEW_PAGES: PreviewPage[] = [
	{key: 'task', labelKey: 'tasklist.themeEditorPreviewTask', path: PREVIEW_PATHS.task, dataset: 'sample'},
	{key: 'tasks', labelKey: 'tasklist.themeEditorPreviewTasks', path: PREVIEW_PATHS.tasks, dataset: 'sample'},
	{
		key: 'processes',
		labelKey: 'tasklist.themeEditorPreviewProcesses',
		path: PREVIEW_PATHS.processes,
		dataset: 'sample',
	},
	{key: 'empty', labelKey: 'tasklist.themeEditorPreviewEmpty', path: PREVIEW_PATHS.tasks, dataset: 'empty'},
];

const PreviewSwitcher: React.FC<Props> = observer(({router}) => {
	const {t} = useTranslation();
	const pathname = useRouterState({router, select: ({location}) => location.pathname});
	const {dataset, setDataset} = themeEditorStore;

	return (
		<div
			role="group"
			aria-label={t('tasklist.themeEditorPreviewSwitcherLabel')}
			className="absolute right-3 bottom-3 z-50 flex items-center gap-1 rounded-lg border border-border bg-popover p-1 shadow-lg"
		>
			{PREVIEW_PAGES.map((page) => {
				const isActive =
					page.dataset === dataset &&
					(page.key === 'task'
						? pathname.startsWith(`${PREVIEW_PATHS.tasks}/`) && pathname !== PREVIEW_PATHS.processes
						: pathname === page.path);

				return (
					<Button
						key={page.key}
						type="button"
						size="xs"
						variant={isActive ? 'secondary' : 'ghost'}
						aria-pressed={isActive}
						onClick={() => {
							setDataset(page.dataset);
							router.history.push(page.path);
						}}
					>
						{t(page.labelKey)}
					</Button>
				);
			})}
		</div>
	);
});

const ThemePreview: React.FC<Props> = observer(({router, queryClient}) => {
	const {t} = useTranslation();
	const frameRef = useRef<HTMLDivElement>(null);

	usePortalScope(frameRef);

	return (
		<section aria-label={t('tasklist.themeEditorPreviewLabel')} className="relative h-full min-h-0 min-w-0">
			<style>{themeEditorStore.previewCss}</style>
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
			<PreviewSwitcher router={router} queryClient={queryClient} />
		</section>
	);
});

export {ThemePreview};
