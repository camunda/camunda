/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button} from '@camunda/design-system';
import {Undo2} from '@camunda/design-system/icons';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {getLastModification} from '../modifications/modificationSelectors';
import {useModificationDescription} from '../useModificationDescription';

const LastModification: React.FC = () => {
	const {t} = useTranslation();
	const {state, dispatch} = useProcessInstanceModification();
	const describe = useModificationDescription();
	const lastModification = getLastModification(state);

	if (lastModification === undefined) {
		return null;
	}

	return (
		<div className="flex items-center gap-4 text-sm">
			<span>
				{t('operate.processInstance.modification.lastModification')}{' '}
				<span className="font-medium">{describe(lastModification)}</span>
			</span>
			<Button variant="ghost" size="sm" onClick={() => dispatch({type: 'removeLastModification'})}>
				<Undo2 aria-hidden="true" data-icon="inline-start" />
				{t('operate.processInstance.modification.undo')}
			</Button>
		</div>
	);
};

export {LastModification};
