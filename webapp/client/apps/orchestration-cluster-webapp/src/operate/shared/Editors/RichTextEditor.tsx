/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, type ComponentProps} from 'react';
import {InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';

const MonacoEditor = lazy(async () => {
	const [{loadMonaco}, {MonacoEditor}] = await Promise.all([
		import('#/shared/monaco/loadMonaco'),
		import('./MonacoEditor'),
	]);
	loadMonaco();
	return {default: MonacoEditor};
});

const RichTextEditor = (props: ComponentProps<typeof MonacoEditor>) => {
	const {t} = useTranslation();
	const loading = props.loading ?? <InlineLoading description={t('operate.shared.editors.loading')} />;

	return (
		<Suspense fallback={loading}>
			<MonacoEditor {...props} loading={loading} />
		</Suspense>
	);
};

export {RichTextEditor};
export type {EditorHandle} from './MonacoEditor';
