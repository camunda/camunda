/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@camunda/design-system';
import {Compass} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {PageEmptyState} from './PageEmptyState';

type Props = {
	reset: () => void;
};

const GenericErrorPage: React.FC<Props> = ({reset}) => {
	const {t} = useTranslation();

	return (
		<PageEmptyState
			icon={<Compass aria-hidden />}
			heading={t('errorGenericErrorPageTitle')}
			description={t('errorGenericErrorPageMessage')}
			action={<Button onClick={reset}>{t('errorGenericErrorPageButtonLabel')}</Button>}
		/>
	);
};

export {GenericErrorPage};
