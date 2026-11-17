/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, ButtonSet} from '@carbon/react';
import {Filter, FilterRemove, SortAscending, SortDescending} from '@carbon/react/icons';
import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.11';

type ConversationTogglesProps = {
	sortOrder: QuerySortOrder;
	canBeScoped: boolean;
	isScoped: boolean;
	onToggleSortOrder: () => void;
	onToggleScope: () => void;
};

const ConversationToggles: React.FC<ConversationTogglesProps> = ({
	sortOrder,
	canBeScoped,
	isScoped,
	onToggleSortOrder,
	onToggleScope,
}) => {
	const {t} = useTranslation();

	return (
		<ButtonSet>
			<Button
				kind="ghost"
				size="xs"
				renderIcon={sortOrder === 'DESC' ? SortDescending : SortAscending}
				onClick={onToggleSortOrder}
			>
				{sortOrder === 'DESC'
					? t('operate.processInstance.details.agent.latestFirst')
					: t('operate.processInstance.details.agent.oldestFirst')}
			</Button>
			{canBeScoped && (
				<Button kind="ghost" size="xs" renderIcon={isScoped ? Filter : FilterRemove} onClick={onToggleScope}>
					{isScoped
						? t('operate.processInstance.details.agent.scopedConversation')
						: t('operate.processInstance.details.agent.wholeConversation')}
				</Button>
			)}
		</ButtonSet>
	);
};

export {ConversationToggles};
