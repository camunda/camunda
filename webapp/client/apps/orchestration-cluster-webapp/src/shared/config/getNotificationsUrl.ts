/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CloudStage} from './getCloudStage';

const NOTIFICATION_URLS: Record<CloudStage, string> = {
	dev: 'https://notifications.cloud.dev.ultrawombat.com',
	int: 'https://notifications.cloud.ultrawombat.com',
	prod: 'https://notifications.cloud.camunda.io',
};

function getNotificationsUrl(stage: CloudStage): string {
	return NOTIFICATION_URLS[stage];
}

export {getNotificationsUrl};
