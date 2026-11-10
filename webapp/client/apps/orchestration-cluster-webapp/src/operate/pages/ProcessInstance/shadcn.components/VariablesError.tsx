/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {ForbiddenError} from '#/shared/errors';

const VariablesError: React.FC<{error: Error | null}> = ({error}) => {
	const {t} = useTranslation();
	const isForbidden = error instanceof ForbiddenError;

	return (
		<div className="flex h-full items-center justify-center">
			<ErrorMessage
				message={t(
					isForbidden ? 'operate.processInstance.variables.forbidden' : 'operate.processInstance.variables.error',
				)}
				additionalInfo={t(
					isForbidden
						? 'operate.processInstance.variables.forbiddenInfo'
						: 'operate.shared.errorMessage.additionalInfo',
				)}
			/>
		</div>
	);
};

export {VariablesError};
