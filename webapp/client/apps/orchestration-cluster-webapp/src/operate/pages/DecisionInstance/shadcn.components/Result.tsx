/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {useDecisionInstance} from '../decisionInstance.queries';

type Props = {
	query: ReturnType<typeof useDecisionInstance>['query'];
};

const Result: React.FC<Props> = ({query}) => {
	const {t} = useTranslation();

	return (
		<div className="relative flex h-full items-center justify-center overflow-hidden">
			{query.isPending && (
				<div data-testid="result-loading-spinner" role="status" aria-live="polite">
					<LoaderCircle className="size-6 animate-spin" aria-hidden />
				</div>
			)}
			{query.isError && <ErrorMessage />}
			{query.isSuccess && query.data.state === 'FAILED' && (
				<EmptyMessage message={t('operate.decisionInstance.variablesPanel.noResult')} />
			)}
			{query.isSuccess && query.data.state !== 'FAILED' && (
				<div data-testid="results-json-viewer" className="absolute inset-0 h-full w-full">
					<RichTextEditor
						value={query.data.result ?? '{}'}
						readOnly
						autoFocus={false}
						height="100%"
						options={{automaticLayout: true}}
					/>
				</div>
			)}
		</div>
	);
};

export {Result};
