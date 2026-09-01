/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {BatchOperationItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator';

type Props = {
	item: BatchOperationItem;
};

const StateCell: React.FC<Props> = ({item}) => {
	const {t} = useTranslation();
	const indicator = <BatchStateIndicator state={item.state} />;

	if (!item.errorMessage) {
		return indicator;
	}

	return (
		<Tooltip
			description={t('operate.batchOperation.itemsTable.failureReason', {message: item.errorMessage})}
			align="bottom"
		>
			<span tabIndex={0} data-testid="item-state-with-error">
				{indicator}
			</span>
		</Tooltip>
	);
};

export {StateCell};
