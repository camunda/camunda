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
import {useModificationDescription} from './useModificationDescription';
import {LastModificationContainer, LastModificationDetail} from './processInstanceModification.styled';

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
