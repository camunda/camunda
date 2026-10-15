/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';

type UserStatusBadgeProps = {
	isActive: boolean;
};

const UserStatusBadge: React.FC<UserStatusBadgeProps> = ({isActive}) => {
	const {t} = useTranslation();

	return (
		<Badge variant={isActive ? 'success' : 'neutral'}>
			{isActive ? t('admin.users.statusActive') : t('admin.users.statusDeactivated')}
		</Badge>
	);
};

export {UserStatusBadge};
