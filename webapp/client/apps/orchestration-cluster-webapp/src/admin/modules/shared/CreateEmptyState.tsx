/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, EmptyState} from '@camunda/design-system';
import {Plus} from '@camunda/design-system/icons';

type CreateEmptyStateProps = {
	resource: string;
	resources: string;
	guideUrl: string;
	onCreate: () => void;
};

const CreateEmptyState: React.FC<CreateEmptyStateProps> = ({resource, resources, guideUrl, onCreate}) => {
	const {t} = useTranslation();

	return (
		<EmptyState
			heading={t('admin.emptyState.title', {resources})}
			description={t('admin.emptyState.description', {resource})}
			action={
				<Button type="button" onClick={onCreate}>
					<Plus data-icon="inline-start" aria-hidden />
					{t('admin.emptyState.createAction', {resource})}
				</Button>
			}
			secondaryAction={
				<Button asChild variant="link">
					<a href={guideUrl} target="_blank" rel="noopener noreferrer">
						{t('admin.emptyState.learnMore', {resources})}
					</a>
				</Button>
			}
		/>
	);
};

export {CreateEmptyState};
