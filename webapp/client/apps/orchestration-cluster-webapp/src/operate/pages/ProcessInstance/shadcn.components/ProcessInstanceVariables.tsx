/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Spinner} from '@camunda/design-system';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {useVariableScope} from '../useVariableScope';
import {variablesQuery} from '../variables.queries';
import {VariablesError} from './VariablesError';
import {VariablesForm} from './VariablesForm';

const CenteredMessage: React.FC<{message: string}> = ({message}) => (
	<div className="flex h-full items-center justify-center">
		<EmptyMessage message={message} />
	</div>
);

const ProcessInstanceVariables: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {isFetchingElementError} = useProcessInstanceElementSelection();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const {scopeKey, isPlaceholder, hasNoContent, hasMultipleInstances, canQuery} = useVariableScope();
	const {data, error, isError, isLoading} = useInfiniteQuery(
		variablesQuery({processInstanceKey: processInstanceId, scopeKey: canQuery ? scopeKey : null}),
	);

	if ((canQuery && isError) || isFetchingElementError) {
		return <VariablesError error={error} />;
	}

	if (hasNoContent) {
		return <CenteredMessage message={t('operate.processInstance.variables.empty')} />;
	}

	if (hasMultipleInstances) {
		return <CenteredMessage message={t('operate.processInstance.variables.multiInstance')} />;
	}

	if (isPlaceholder || (isModificationModeEnabled && scopeKey === null)) {
		return scopeKey === null ? (
			<CenteredMessage message={t('operate.processInstance.variables.empty')} />
		) : (
			<div className="relative flex h-full flex-col">
				<VariablesForm key={`${scopeKey}-${isModificationModeEnabled}`} scopeKey={scopeKey} status="empty" />
			</div>
		);
	}

	const isPending = scopeKey === null || isLoading || data === undefined;

	return (
		<div className="relative flex h-full flex-col">
			{isPending && (
				<div className="absolute inset-0 z-10 flex items-center justify-center bg-background/60">
					<Spinner />
				</div>
			)}
			{scopeKey !== null && (
				<VariablesForm
					key={`${scopeKey}-${isModificationModeEnabled}`}
					scopeKey={scopeKey}
					status={isPending ? 'loading' : data.pages[0]?.items.length === 0 ? 'empty' : 'variables'}
				/>
			)}
		</div>
	);
};

export {ProcessInstanceVariables};
