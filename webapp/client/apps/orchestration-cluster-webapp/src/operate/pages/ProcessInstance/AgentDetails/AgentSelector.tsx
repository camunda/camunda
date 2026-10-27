/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {OnChangeData} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {Dropdown} from './agentDetails.styled';

type SelectableAgentInstance = {
	agentInstanceKey: string;
	label: string;
};

type AgentSelectorProps = {
	selectedAgentInstanceKey: string;
	agents: SelectableAgentInstance[];
	remainingAgentsCount?: string;
	onChange: (agentInstanceKey: string) => void;
};

const AgentSelector: React.FC<AgentSelectorProps> = ({
	selectedAgentInstanceKey,
	remainingAgentsCount,
	agents,
	onChange,
}) => {
	const {t} = useTranslation();
	const selectedAgent = agents.find((agent) => agent.agentInstanceKey === selectedAgentInstanceKey) ?? null;
	const label = t('operate.processInstance.details.agent.currentAgent');

	return (
		<Dropdown
			id="agent-instance-selector"
			size="xs"
			type="inline"
			label={label}
			titleText={label}
			hideLabel
			items={
				remainingAgentsCount
					? [
							...agents,
							{
								label: t('operate.processInstance.details.agent.agentsNotShown', {value: remainingAgentsCount}),
								disabled: true,
							},
						]
					: agents
			}
			selectedItem={selectedAgent}
			onChange={(data: OnChangeData<SelectableAgentInstance | null>) => {
				if (data.selectedItem !== null) {
					onChange(data.selectedItem.agentInstanceKey);
				}
			}}
		/>
	);
};

export {AgentSelector};
