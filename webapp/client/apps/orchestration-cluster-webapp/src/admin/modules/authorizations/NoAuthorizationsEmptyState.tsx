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

type NoAuthorizationsEmptyStateProps = {
	onCreate: () => void;
};

const NoAuthorizationsEmptyState: React.FC<NoAuthorizationsEmptyStateProps> = ({onCreate}) => {
	const {t} = useTranslation();

	return (
		<EmptyState
			heading={t('admin.authorizations.noAuthorizationsYet')}
			description={t('admin.authorizations.noAuthorizationsYetDescription')}
			action={
				<Button type="button" onClick={onCreate}>
					<Plus data-icon="inline-start" aria-hidden />
					{t('admin.authorizations.createAuthorization')}
				</Button>
			}
		/>
	);
};

export {NoAuthorizationsEmptyState};
