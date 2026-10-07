/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import c4FormJsCss from '@bpmn-io/c4-theme/assets/form-js.css?url';
import c4TokensCss from '@bpmn-io/c4-theme/assets/tokens.css?url';
import formJsCss from '@bpmn-io/form-js-viewer/dist/assets/form-js.css?url';
import {createFileRoute} from '@tanstack/react-router';
import {ThemeEditorPage} from '#/tasklist/pages/ThemeEditorPage';

export const Route = createFileRoute('/_shadcn/_auth/tasklist/theme-editor')({
	// The editor needs the space, and the preview draws its own header and sidebar.
	staticData: {hideAppChrome: true},
	head: () => ({
		meta: [
			{
				title: 'Theme editor - Tasklist - Camunda',
			},
		],
		// The preview renders a form-js task form, which needs the same styles as the real task page.
		links: [
			{rel: 'stylesheet', href: formJsCss},
			{rel: 'stylesheet', href: c4TokensCss},
			{rel: 'stylesheet', href: c4FormJsCss},
		],
	}),
	component: ThemeEditorPage,
});
