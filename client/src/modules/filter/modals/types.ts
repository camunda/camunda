/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';

import {Definition, FilterData, Variable} from 'types';

export type MultipleVarialbeFilterConfig = {
  getValues: (
    name: string,
    type: string,
    valuesCount: number,
    valueFilter: string,
    definition: Definition
  ) => Promise<(string | number | boolean | null)[]>;
  getVariables: (definition: Definition) => Promise<Variable[]>;
};
export type FilterLevel = 'instance' | 'view';

export type InstanceFilterType =
  | 'instanceState'
  | 'instanceStartDate'
  | 'instanceEndDate'
  | 'flowNodeStartDate'
  | 'flowNodeEndDate'
  | 'processInstanceDuration'
  | 'flowNodeDuration'
  | 'executedFlowNodes'
  | 'incidentInstances'
  | 'assignee'
  | 'candidateGroup'
  | 'multipleVariable'
  | 'executingFlowNodes'
  | 'canceledFlowNodes';

export type ViewFilterType =
  | 'flowNodeStatus'
  | 'flowNodeStartDate'
  | 'flowNodeEndDate'
  | 'flowNodeDuraion'
  | 'incident'
  | 'assignee'
  | 'candidateGroup'
  | 'executedFlowNodes'
  | 'executingFlowNodes'
  | 'canceledFlowNodes';

export type InstanceStateFilterType =
  | 'runningInstancesOnly'
  | 'completedInstancesOnly'
  | 'canceledInstancesOnly'
  | 'nonCanceledInstancesOnly'
  | 'suspendedInstancesOnly'
  | 'nonSuspendedInstancesOnly';

export type FlowNodeStateFilterType =
  | 'runningFlowNodesOnly'
  | 'completedFlowNodesOnly'
  | 'canceledFlowNodesOnly'
  | 'completedOrCanceledFlowNodesOnly';

export type IncidentFilterType =
  | 'includesOpenIncident'
  | 'includesResolvedIncident'
  | 'includesClosedIncident'
  | 'doesNotIncludeIncident';

export interface FilterProps<T = FilterData> {
  addFilter: (filter: FilterProps<T>['filterData']) => void;
  className?: string;
  close: () => void;
  config?: MultipleVarialbeFilterConfig | undefined;
  definitions: Definition[];
  filterLevel: FilterLevel;
  filterType: ViewFilterType | InstanceFilterType;
  filterData?: {
    type: string;
    appliedTo: (string | undefined)[];
    data: T;
  };
  reportIds?: string[];
  forceEnabled?: (...args: any[]) => boolean;
  getPretext?: (...args: any[]) => ReactNode;
  getPosttext?: (...args: any[]) => ReactNode;
}
