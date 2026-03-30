/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	adHocSubProcessActivateActivitiesInstructionSchema,
	adHocSubProcessActivateActivityReferenceSchema,
} from './gen';

const activatableActivitySchema = adHocSubProcessActivateActivityReferenceSchema;
type ActivatableActivity = z.infer<typeof activatableActivitySchema>;

const activateActivityWithinAdHocSubProcessRequestBodySchema = adHocSubProcessActivateActivitiesInstructionSchema;
type ActivateActivityWithinAdHocSubProcessRequestBody = z.infer<
	typeof activateActivityWithinAdHocSubProcessRequestBodySchema
>;

const activateAdHocSubProcessActivities: Endpoint<{
	adHocSubProcessInstanceKey: string;
}> = {
	method: 'POST',
	getUrl: ({adHocSubProcessInstanceKey}) =>
		`/${API_VERSION}/element-instances/ad-hoc-activities/${adHocSubProcessInstanceKey}/activation`,
};

export {activateActivityWithinAdHocSubProcessRequestBodySchema, activateAdHocSubProcessActivities};

export type {ActivatableActivity, ActivateActivityWithinAdHocSubProcessRequestBody};
