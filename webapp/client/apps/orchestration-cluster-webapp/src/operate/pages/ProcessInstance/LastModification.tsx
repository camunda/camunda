/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {getLastModification} from './modifications/modificationSelectors';
import type {Modification} from './modifications/modificationReducer';
import {LastModificationContainer, LastModificationDetail} from './processInstanceModification.styled';

function useModificationDescription() {
	const {t} = useTranslation();

	return ({type, payload}: Modification) => {
		if (type === 'variable') {
			return t(
				payload.operation === 'ADD_VARIABLE'
					? 'operate.processInstance.modification.lastAddVariable'
					: 'operate.processInstance.modification.lastEditVariable',
				{name: payload.name},
			);
		}

		switch (payload.operation) {
			case 'ADD_TOKEN':
				return t('operate.processInstance.modification.lastAddToken', {element: payload.element.name});
			case 'CANCEL_TOKEN':
				return t('operate.processInstance.modification.lastCancelToken', {element: payload.element.name});
			case 'MOVE_TOKEN':
				return t('operate.processInstance.modification.lastMoveToken', {
					source: payload.element.name,
					target: payload.targetElement.name,
				});
		}
	};
}

function LastModification() {
	const {t} = useTranslation();
	const {state, dispatch} = useProcessInstanceModification();
	const describe = useModificationDescription();
	const lastModification = getLastModification(state);

	if (lastModification === undefined) {
		return null;
	}

	return (
		<LastModificationContainer>
			<div>
				{t('operate.processInstance.modification.lastModification')}{' '}
				<LastModificationDetail>{describe(lastModification)}</LastModificationDetail>
			</div>
			<Button kind="secondary" size="sm" onClick={() => dispatch({type: 'removeLastModification'})}>
				{t('operate.processInstance.modification.undo')}
			</Button>
		</LastModificationContainer>
	);
}

export {LastModification};
