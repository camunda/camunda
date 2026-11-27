/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EmptyState} from '@camunda/design-system';
import {CircleAlert, LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';
import {DataLoadErrorAlert} from '#/operate/shared/DataLoadErrorAlert/shadcn.components/DataLoadErrorAlert';
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
					<span className="sr-only">{t('operate.decisionInstance.ds.variablesPanel.loadingResult')}</span>
				</div>
			)}
			{query.isError && <DataLoadErrorAlert onRetry={() => void query.refetch()} />}
			{query.isSuccess && query.data.state === 'FAILED' && (
				<EmptyState
					size="sm"
					headingLevel={3}
					icon={<CircleAlert aria-hidden />}
					heading={t('operate.decisionInstance.ds.variablesPanel.noResultHeading')}
					description={t('operate.decisionInstance.ds.variablesPanel.evaluationFailedDescription')}
				/>
			)}
			{query.isSuccess && query.data.state !== 'FAILED' && (
				<div data-testid="results-json-viewer" className="h-full w-full">
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
