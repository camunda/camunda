/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getClientConfig} from './getClientConfig';

type CloudStage = 'dev' | 'int' | 'prod';

function getCloudStage(): CloudStage | undefined {
	const stage = getClientConfig().cloud.stage;
	return stage === 'dev' || stage === 'int' || stage === 'prod' ? stage : undefined;
}

export {getCloudStage};
export type {CloudStage};
