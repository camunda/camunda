/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const PROCESS_INSTANCE_TABS = ['variables', 'incidents', 'listeners', 'details'] as const;

type ProcessInstanceTab = (typeof PROCESS_INSTANCE_TABS)[number];

function getProcessInstanceTabFromPath(pathname: string): ProcessInstanceTab {
	const segment = pathname.split('/').filter(Boolean).pop();

	return PROCESS_INSTANCE_TABS.find((tab) => tab === segment) ?? 'variables';
}

export {PROCESS_INSTANCE_TABS, getProcessInstanceTabFromPath};
export type {ProcessInstanceTab};
