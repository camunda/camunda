/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {activateAdHocSubProcessActivitiesStatus204Schema} from './gen/zod/activateAdHocSubProcessActivitiesSchema';
import {adHocSubProcessActivateActivitiesInstructionSchema} from './gen/zod/adHocSubProcessActivateActivitiesInstructionSchema';
import type {ActivateAdHocSubProcessActivitiesStatus204} from './gen/types/ActivateAdHocSubProcessActivities';
import type {AdHocSubProcessActivateActivitiesInstruction} from './gen/types/AdHocSubProcessActivateActivitiesInstruction';

const activateActivityWithinAdHocSubProcessRequestBodySchema = adHocSubProcessActivateActivitiesInstructionSchema;
type ActivateActivityWithinAdHocSubProcessRequestBody = AdHocSubProcessActivateActivitiesInstruction;

const activateActivityWithinAdHocSubProcessResponseBodySchema = activateAdHocSubProcessActivitiesStatus204Schema;
type ActivateActivityWithinAdHocSubProcessResponseBody = ActivateAdHocSubProcessActivitiesStatus204;

const activateAdHocSubProcessActivities = {
	method: 'POST',
	getUrl: ({adHocSubProcessInstanceKey}) =>
		`/${API_VERSION}/element-instances/ad-hoc-activities/${adHocSubProcessInstanceKey}/activation` as const,
} as const satisfies Endpoint<{
	adHocSubProcessInstanceKey: string;
}>;

export {
	activateActivityWithinAdHocSubProcessRequestBodySchema,
	activateActivityWithinAdHocSubProcessResponseBodySchema,
	activateAdHocSubProcessActivities,
};

export type {ActivateActivityWithinAdHocSubProcessRequestBody, ActivateActivityWithinAdHocSubProcessResponseBody};
