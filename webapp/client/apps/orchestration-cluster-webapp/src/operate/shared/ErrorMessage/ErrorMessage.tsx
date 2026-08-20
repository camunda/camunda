/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {EmptyMessage} from '../EmptyMessage/EmptyMessage';

type Props = {
	message?: string;
	additionalInfo?: string;
};

const ErrorMessage: React.FC<Props> = (props) => {
	const {t} = useTranslation();
	const defaultError = {
		message: t('operate.shared.errorMessage.message'),
		additionalInfo: t('operate.shared.errorMessage.additionalInfo'),
	};
	return <EmptyMessage {...defaultError} {...props} />;
};

export {ErrorMessage};
