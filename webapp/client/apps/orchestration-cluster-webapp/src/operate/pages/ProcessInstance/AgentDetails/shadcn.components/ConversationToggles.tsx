/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button} from '@camunda/design-system';
import {ArrowDownWideNarrow, ArrowUpWideNarrow, Filter, FilterX} from '@camunda/design-system/icons';
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
		<div className="flex flex-wrap gap-2">
			<Button variant="ghost" size="sm" onClick={onToggleSortOrder}>
				{sortOrder === 'DESC' ? (
					<ArrowDownWideNarrow className="size-4" aria-hidden />
				) : (
					<ArrowUpWideNarrow className="size-4" aria-hidden />
				)}
				{sortOrder === 'DESC'
					? t('operate.processInstance.details.agent.latestFirst')
					: t('operate.processInstance.details.agent.oldestFirst')}
			</Button>
			{canBeScoped && (
				<Button variant="ghost" size="sm" onClick={onToggleScope}>
					{isScoped ? <Filter className="size-4" aria-hidden /> : <FilterX className="size-4" aria-hidden />}
					{isScoped
						? t('operate.processInstance.details.agent.scopedConversation')
						: t('operate.processInstance.details.agent.wholeConversation')}
				</Button>
			)}
		</div>
	);
};

export {ConversationToggles};
