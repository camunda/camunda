/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Loading} from '@carbon/react';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useVariableScope} from './useVariableScope';
import {variablesQuery} from './variables.queries';
import {VariablesError} from './VariablesError';
import {VariablesForm} from './VariablesForm';
import {Content, EmptyMessageContainer} from './variables.styled';

const ProcessInstanceVariables: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {isFetchingElementError} = useProcessInstanceElementSelection();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const {scopeKey, isPlaceholder, hasNoContent, hasMultipleInstances, canQuery} = useVariableScope();
	const {data, error, isError, isLoading} = useInfiniteQuery(
		variablesQuery({processInstanceKey: processInstanceId, scopeKey: canQuery ? scopeKey : null}),
	);
	const emptyMessage = (
		<EmptyMessageContainer>
			<EmptyMessage message={t('operate.processInstance.variables.empty')} />
		</EmptyMessageContainer>
	);

	if ((canQuery && isError) || isFetchingElementError) {
		return <VariablesError error={error} />;
	}

	if (hasNoContent) {
		return emptyMessage;
	}

	if (hasMultipleInstances) {
		return (
			<EmptyMessageContainer>
				<EmptyMessage message={t('operate.processInstance.variables.multiInstance')} />
			</EmptyMessageContainer>
		);
	}

	if (isPlaceholder || (isModificationModeEnabled && scopeKey === null)) {
		return scopeKey === null ? (
			emptyMessage
		) : (
			<Content>
				<VariablesForm key={`${scopeKey}-${isModificationModeEnabled}`} scopeKey={scopeKey} status="empty" />
			</Content>
		);
	}

	const isPending = scopeKey === null || isLoading || data === undefined;

	return (
		<Content>
			{isPending && <Loading data-testid="variables-spinner" />}
			{scopeKey !== null && (
				<VariablesForm
					key={`${scopeKey}-${isModificationModeEnabled}`}
					scopeKey={scopeKey}
					status={isPending ? 'loading' : data.pages[0]?.items.length === 0 ? 'empty' : 'variables'}
				/>
			)}
		</Content>
	);
};

export {ProcessInstanceVariables};
