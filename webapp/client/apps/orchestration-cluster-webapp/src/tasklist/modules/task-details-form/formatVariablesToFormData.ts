/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';

type Variable = {
	name: string;
	value: string | null;
};

function formatVariablesToFormData(variables: Variable[]) {
	return variables.reduce<Record<string, unknown>>((accumulator, {name, value}) => {
		accumulator[name] = value === null ? '' : tryParseJSON(value);
		return accumulator;
	}, {});
}

export {formatVariablesToFormData};
