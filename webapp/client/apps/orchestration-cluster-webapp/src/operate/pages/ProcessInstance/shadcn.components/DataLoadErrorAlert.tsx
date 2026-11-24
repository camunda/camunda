/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Alert, Button} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type Props = {
	title?: string;
	description?: string;
	onRetry?: () => void;
	className?: string;
};

const DataLoadErrorAlert: React.FC<Props> = ({title, description, onRetry, className}) => {
	const {t} = useTranslation();

	return (
		<div className={cn('w-full p-4', className)}>
			<Alert variant="destructive" title={title ?? t('operate.shared.errorMessage.message')} description={description}>
				{onRetry !== undefined && (
					<Button variant="secondary" size="sm" onClick={onRetry}>
						{t('errorGenericErrorPageButtonLabel')}
					</Button>
				)}
			</Alert>
		</div>
	);
};

export {DataLoadErrorAlert};
