/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const PROCESS_INSTANCE_TABS = [
	'variables',
	'input-mappings',
	'output-mappings',
	'incidents',
	'listeners',
	'operations-log',
	'details',
] as const;

type ProcessInstanceTab = (typeof PROCESS_INSTANCE_TABS)[number];

const TAB_LABEL_KEYS = {
	variables: 'operate.processInstance.history.variables',
	'input-mappings': 'operate.processInstance.history.inputMappings',
	'output-mappings': 'operate.processInstance.history.outputMappings',
	incidents: 'operate.processInstance.history.incidents',
	listeners: 'operate.processInstance.history.listeners',
	'operations-log': 'operate.processInstance.history.operationsLog',
	details: 'operate.processInstance.history.details',
} as const satisfies Record<ProcessInstanceTab, string>;

const SELECTION_ONLY_TABS: readonly ProcessInstanceTab[] = ['input-mappings', 'output-mappings'];

function getVisibleProcessInstanceTabs(hasSelection: boolean): readonly ProcessInstanceTab[] {
	return PROCESS_INSTANCE_TABS.filter((tab) => hasSelection || !SELECTION_ONLY_TABS.includes(tab));
}

function getProcessInstanceTabFromPath(pathname: string): ProcessInstanceTab {
	const segment = pathname.split('/').filter(Boolean).pop();

	return PROCESS_INSTANCE_TABS.find((tab) => tab === segment) ?? 'variables';
}

type TabRedirectInput = {
	activeTab: ProcessInstanceTab;
	visibleTabs: readonly ProcessInstanceTab[];
	hasSelection: boolean;
	isProcessLevelWaiting: boolean;
	isWaitStateLoading: boolean;
};

function getHiddenTabRedirect({
	activeTab,
	visibleTabs,
	hasSelection,
	isProcessLevelWaiting,
	isWaitStateLoading,
}: TabRedirectInput): 'details' | 'variables' | null {
	if (visibleTabs.includes(activeTab) || (!hasSelection && isWaitStateLoading)) {
		return null;
	}

	return hasSelection || isProcessLevelWaiting ? 'details' : 'variables';
}

export {
	PROCESS_INSTANCE_TABS,
	TAB_LABEL_KEYS,
	getHiddenTabRedirect,
	getProcessInstanceTabFromPath,
	getVisibleProcessInstanceTabs,
};
export type {ProcessInstanceTab};
