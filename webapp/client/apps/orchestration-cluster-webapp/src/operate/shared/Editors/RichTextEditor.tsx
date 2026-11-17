/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, type ComponentProps} from 'react';
import {InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {EditorStyles} from './editorStyles';
import {LazyMonacoEditor} from './LazyMonacoEditor';

const RichTextEditor = (props: ComponentProps<typeof LazyMonacoEditor>) => {
	const {t} = useTranslation();
	const loading = props.loading ?? <InlineLoading description={t('operate.shared.editors.loading')} />;

	return (
		<>
			<EditorStyles />
			<Suspense fallback={loading}>
				<LazyMonacoEditor {...props} loading={loading} />
			</Suspense>
		</>
	);
};

export {RichTextEditor};
export type {EditorHandle} from './MonacoEditor';
