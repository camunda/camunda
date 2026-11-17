/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';
import {isValidJSON} from '#/shared/json/isValidJSON';

function validateClusterVariableValue(value: string | undefined, t: TFunction): string | undefined {
	const trimmed = value?.trim();

	if (!trimmed) {
		return t('admin.clusterVariables.valueRequiredError');
	}

	if (!isValidJSON(trimmed)) {
		return t('admin.clusterVariables.valueInvalidError');
	}

	// The API rejects a null value as empty.
	if (JSON.parse(trimmed) === null) {
		return t('admin.clusterVariables.valueNullError');
	}

	return undefined;
}

export {validateClusterVariableValue};
