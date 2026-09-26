/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Loading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {RichTextEditor} from '#/operate/shared/Editors/RichTextEditor';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {useDecisionInstance} from './decisionInstance.queries';
import {JsonViewer, ResultContainer} from './variablesPanel.styled';

type Props = {
	query: ReturnType<typeof useDecisionInstance>['query'];
};

const Result: React.FC<Props> = ({query}) => {
	const {t} = useTranslation();

	return (
		<ResultContainer>
			{query.isPending && <Loading data-testid="result-loading-spinner" withOverlay={false} />}
			{query.isError && <ErrorMessage />}
			{query.isSuccess && query.data.state === 'FAILED' && (
				<EmptyMessage message={t('operate.decisionInstance.variablesPanel.noResult')} />
			)}
			{query.isSuccess && query.data.state !== 'FAILED' && (
				<JsonViewer data-testid="results-json-viewer">
					<RichTextEditor
						value={query.data.result ?? '{}'}
						readOnly
						autoFocus={false}
						height="100%"
						options={{automaticLayout: true}}
					/>
				</JsonViewer>
			)}
		</ResultContainer>
	);
};

export {Result};
