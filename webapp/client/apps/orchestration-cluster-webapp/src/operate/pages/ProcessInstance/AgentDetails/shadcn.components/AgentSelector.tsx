/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@camunda/design-system';

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
		<Select value={selectedAgentInstanceKey} onValueChange={onChange}>
			<SelectTrigger id="agent-instance-selector" size="sm" aria-label={label} className="h-8 max-w-[26rem]">
				<SelectValue>{selectedAgent?.label}</SelectValue>
			</SelectTrigger>
			<SelectContent>
				{agents.map((agent) => (
					<SelectItem key={agent.agentInstanceKey} value={agent.agentInstanceKey}>
						{agent.label}
					</SelectItem>
				))}
				{remainingAgentsCount !== undefined && (
					<SelectItem value="hidden-agents" disabled>
						{t('operate.processInstance.details.agent.agentsNotShown', {value: remainingAgentsCount})}
					</SelectItem>
				)}
			</SelectContent>
		</Select>
	);
};

export {AgentSelector};
