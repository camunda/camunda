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
import {GUIDE_URLS} from './guideUrls';

type MemberKind = 'users' | 'groups' | 'roles' | 'mappingRules' | 'clients';

type AssignEmptyStateProps = {
	parent: 'roles' | 'groups' | 'tenants';
	kind: MemberKind;
	description: string;
	onAssign: () => void;
};

const AssignEmptyState: React.FC<AssignEmptyStateProps> = ({parent, kind, description, onAssign}) => {
	const {t} = useTranslation();
	const resources = t(`admin.members.kinds.${kind}`);

	return (
		<EmptyState
			heading={t('admin.members.emptyState.title', {
				resources,
				parent: t(`admin.${parent}.resourceSingular`),
			})}
			description={description}
			action={
				<Button type="button" onClick={onAssign}>
					<Plus data-icon="inline-start" aria-hidden />
					{t(`admin.${parent}.members.${kind}.assign`)}
				</Button>
			}
			secondaryAction={
				<Button asChild variant="link">
					<a href={GUIDE_URLS[kind]} target="_blank" rel="noopener noreferrer">
						{t('admin.members.emptyState.learnMore', {resources})}
					</a>
				</Button>
			}
		/>
	);
};

export {AssignEmptyState};
