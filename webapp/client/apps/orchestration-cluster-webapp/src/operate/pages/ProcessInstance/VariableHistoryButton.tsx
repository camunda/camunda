/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';
import {Time} from '@carbon/react/icons';
import {VariableHistoryModal} from './VariableHistoryModal';
import type {VariableHistoryTarget} from './variableHistory.queries';

type Props = {
	target: VariableHistoryTarget;
};

const VariableHistoryButton: React.FC<Props> = ({target}) => {
	const [isOpen, setIsOpen] = useState(false);

	return (
		<>
			<Button
				kind="ghost"
				hasIconOnly
				renderIcon={Time}
				size="sm"
				aria-label={`View history of ${target.name}`}
				iconDescription="View history"
				tooltipPosition="top"
				onClick={() => setIsOpen(true)}
			/>
			{isOpen && <VariableHistoryModal target={target} onClose={() => setIsOpen(false)} />}
		</>
	);
};

export {VariableHistoryButton};
