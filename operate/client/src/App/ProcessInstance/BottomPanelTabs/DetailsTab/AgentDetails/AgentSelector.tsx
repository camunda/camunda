/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dropdown, type OnChangeData} from '@carbon/react';
import {useCallback, useMemo} from 'react';

type SelectableAgentInstance = {
  agentInstanceKey: string;
  label: string;
};

type AgentSelectorProps = {
  selectedAgentInstanceKey: string;
  agents: SelectableAgentInstance[];
  /** Amount of agents (e.g. "200" or "600+") that are hidden from the list by pagination. */
  remainingAgentsCount?: string;
  onChange: (agentInstanceKey: string) => void;
};

const AgentSelector: React.FC<AgentSelectorProps> = ({
  selectedAgentInstanceKey,
  remainingAgentsCount,
  agents,
  onChange,
}) => {
  const selectedAgent = useMemo(
    () =>
      agents.find(
        (agent) => agent.agentInstanceKey === selectedAgentInstanceKey,
      ) ?? null,
    [agents, selectedAgentInstanceKey],
  );

  const handleChange = useCallback(
    (data: OnChangeData<SelectableAgentInstance | null>) => {
      if (data.selectedItem !== null) {
        onChange(data.selectedItem.agentInstanceKey);
      }
    },
    [onChange],
  );

  return (
    <Dropdown
      id="agent-instance-selector"
      size="xs"
      type="inline"
      label="Current AI agent"
      titleText="Current AI agent"
      hideLabel
      items={
        remainingAgentsCount
          ? [
              ...agents,
              {
                label: `${remainingAgentsCount} AI agents not shown`,
                disabled: true,
              },
            ]
          : agents
      }
      selectedItem={selectedAgent}
      onChange={handleChange}
    />
  );
};

export {AgentSelector, type SelectableAgentInstance};
