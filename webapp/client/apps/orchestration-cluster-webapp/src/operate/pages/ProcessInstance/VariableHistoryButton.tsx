/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext} from 'react';
import {Button} from '@carbon/react';
import {Time} from '@carbon/react/icons';
import {VariableHistoryViewContext} from './VariableHistoryViewContext';
import type {VariableHistoryTarget} from './variableHistory.queries';

type Props = {
	target: VariableHistoryTarget;
};

const VariableHistoryButton: React.FC<Props> = ({target}) => {
	const showHistory = useContext(VariableHistoryViewContext);

	return (
		<Button
			kind="ghost"
			hasIconOnly
			renderIcon={Time}
			size="sm"
			aria-label={`View history of ${target.name}`}
			iconDescription="View history"
			tooltipPosition="top"
			onClick={() => showHistory(target)}
		/>
	);
};

export {VariableHistoryButton};
