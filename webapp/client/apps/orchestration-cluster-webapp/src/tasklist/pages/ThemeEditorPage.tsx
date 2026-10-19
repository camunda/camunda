/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {useEffect, useState} from 'react';
import {Customizer} from '#/tasklist/modules/theme-editor/components/Customizer';
import {ThemePreview} from '#/tasklist/modules/theme-editor/preview/ThemePreview';
import {createPreviewQueryClient, createPreviewRouter} from '#/tasklist/modules/theme-editor/preview/previewRouter';

function isTyping(target: EventTarget | null) {
	return (
		target instanceof HTMLInputElement ||
		target instanceof HTMLTextAreaElement ||
		target instanceof HTMLSelectElement ||
		(target instanceof HTMLElement && target.isContentEditable)
	);
}

const ThemeEditorPage: React.FC = observer(() => {
	const [router] = useState(createPreviewRouter);
	const [queryClient] = useState(createPreviewQueryClient);
	const [isResetDialogOpen, setIsResetDialogOpen] = useState(false);

	useEffect(() => {
		const handleKeyDown = (event: KeyboardEvent) => {
			if (event.metaKey || event.ctrlKey || event.altKey || event.repeat || isTyping(event.target)) {
				return;
			}

			if (event.key === 'R' && event.shiftKey) {
				event.preventDefault();
				setIsResetDialogOpen(true);
			}
		};

		document.addEventListener('keydown', handleKeyDown);
		return () => document.removeEventListener('keydown', handleKeyDown);
	}, []);

	return (
		// Avoid responsive pairs such as `flex-col md:flex-row`: the design system ships its own
		// Tailwind build, which loads later and makes the base utility win over the variant.
		<div className="grid h-dvh min-h-0 grid-cols-[16rem_minmax(0,1fr)] bg-background grid-rows-[minmax(0,1fr)] gap-4 p-4">
			<Customizer isResetDialogOpen={isResetDialogOpen} onResetDialogOpenChange={setIsResetDialogOpen} />
			<ThemePreview router={router} queryClient={queryClient} />
		</div>
	);
});

export {ThemeEditorPage};
