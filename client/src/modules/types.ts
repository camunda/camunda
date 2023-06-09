/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SerializedEditorState} from 'lexical';

type ReportType = 'process' | 'decision';

interface Report<Data> {
  name: string;
  id: string;
  owner: string;
  lastModified: string;
  collectionId: string;
  created: string;
  lastModifier: string;
  data: Data;
  combined: boolean;
  reportType: ReportType;
}

type FilterFilterApplicationLevel = 'instance' | 'view';

interface ProcessFilter<DATA> {
  type:
    | 'assignee'
    | 'canceledFlowNodes'
    | 'canceledFlowNodesOnly'
    | 'canceledInstancesOnly'
    | 'candidateGroup'
    | 'completedFlowNodesOnly'
    | 'completedInstancesOnly'
    | 'completedOrCanceledFlowNodesOnly'
    | 'includesClosedIncident'
    | 'processInstanceDuration'
    | 'executedFlowNodes'
    | 'executingFlowNodes'
    | 'flowNodeDuration'
    | 'flowNodeEndDate'
    | 'flowNodeStartDate'
    | 'instanceEndDate'
    | 'instanceStartDate'
    | 'multipleVariable'
    | 'doesNotIncludeIncident'
    | 'nonCanceledInstancesOnly'
    | 'nonSuspendedInstancesOnly'
    | 'includesOpenIncident'
    | 'includesResolvedIncident'
    | 'runningFlowNodesOnly'
    | 'runningInstancesOnly'
    | 'suspendedInstancesOnly'
    | 'UserTaskFlowNodesOnlyFilterDto'
    | 'variable';
  data: DATA;
  filterLevel: FilterFilterApplicationLevel;
  appliedTo: string[];
}

interface DecisionFilter<DATA> {
  type: 'evaluationDateTime' | 'inputVariable' | 'outputVariable';
  data: DATA;
  appliedTo: string[];
}

type VariableType =
  | 'String'
  | 'Short'
  | 'Long'
  | 'Double'
  | 'Integer'
  | 'Boolean'
  | 'Date'
  | 'Object'
  | 'Json';

type ViewProcessViewEntity = 'flowNode' | 'userTask' | 'processInstance' | 'variable' | 'incident';

interface SingleViewProperty {
  name: string;
  type: VariableType;
}

interface ProcessView {
  entity: ViewProcessViewEntity;
  properties: SingleViewProperty[];
}

interface DecisionView {
  properties: SingleViewProperty[];
}

interface GroupProcessGroupByDto<VALUE> {
  type:
    | 'assignee'
    | 'candidateGroup'
    | 'duration'
    | 'endDate'
    | 'flowNodes'
    | 'none'
    | 'runningDate'
    | 'startDate'
    | 'userTasks'
    | 'variable';
  value: VALUE;
}

interface GroupDecisionGroupByDto<VALUE> {
  type: 'evaluationDateTime' | 'inputVariable' | 'matchedRule' | 'none' | 'outputVariable';
  value: VALUE;
}

interface DistributedBy<VALUE> {
  type:
    | 'assignee'
    | 'candidateGroup'
    | 'endDate'
    | 'flowNode'
    | 'none'
    | 'process'
    | 'startDate'
    | 'userTask'
    | 'variable';
  value: VALUE;
}

type ProcessVisualization = 'number' | 'table' | 'bar' | 'barLine' | 'line' | 'pie' | 'heat';

type DecisionVisualization = 'number' | 'table' | 'bar' | 'line' | 'pie' | 'heat';

interface ConfigurationAggregation {
  type: 'avg' | 'min' | 'max' | 'sum' | 'percentile';
  value: number;
}

type Target_valueTargetValueUnit =
  | 'millis'
  | 'seconds'
  | 'minutes'
  | 'hours'
  | 'days'
  | 'weeks'
  | 'months'
  | 'years';

type GroupAggregateByDateUnit = 'year' | 'month' | 'week' | 'day' | 'hour' | 'minute' | 'automatic';

interface ConfigurationTableColumn {
  includeNewVariables: boolean;
  excludedColumns: string[];
  includedColumns: string[];
  columnOrder: string[];
}

interface Target_valueSingleReportCountChartDto {
  isBelow: boolean;
  value: string;
}

interface Target_valueBaseLineDto {
  unit: Target_valueTargetValueUnit;
  value: string;
}

interface Target_valueTargetDto {
  unit: Target_valueTargetValueUnit;
  value: string;
  isBelow: boolean;
}

interface Target_valueDurationProgressDto {
  baseline: Target_valueBaseLineDto;
  target: Target_valueTargetDto;
}

interface Target_valueCountProgressDto {
  baseline: string;
  target: string;
  isBelow: boolean;
}

interface Target_valueSingleReportDurationChartDto {
  unit: Target_valueTargetValueUnit;
  isBelow: boolean;
  value: string;
}

interface Target_valueSingleReportTargetValueDto {
  countChart: Target_valueSingleReportCountChartDto;
  durationProgress: Target_valueDurationProgressDto;
  active: boolean;
  countProgress: Target_valueCountProgressDto;
  durationChart: Target_valueSingleReportDurationChartDto;
  isKpi: boolean;
}

interface Heatmap_target_valueHeatmapTargetValueEntryDto {
  unit: Target_valueTargetValueUnit;
  value: string;
}

interface Heatmap_target_valueHeatmapTargetValueDto {
  active: boolean;
  values: Record<string, Heatmap_target_valueHeatmapTargetValueEntryDto>;
}

type Custom_bucketsBucketUnit =
  | 'year'
  | 'month'
  | 'week'
  | 'day'
  | 'hour'
  | 'minute'
  | 'second'
  | 'millisecond';

interface CustomBucket {
  active: boolean;
  bucketSize: number;
  bucketSizeUnit: Custom_bucketsBucketUnit;
  baseline: number;
  baselineUnit: Custom_bucketsBucketUnit;
}

type SortingSortOrder = 'asc' | 'desc';

interface SortingReportSortingDto {
  by?: string | null;
  order?: SortingSortOrder | null;
}

interface Process_partProcessPartDto {
  start: string;
  end: string;
}

interface ConfigurationMeasureVisualizationsDto {
  frequency: string;
  duration: string;
}

interface SingleReportConfiguration {
  color: string;
  aggregationTypes: ConfigurationAggregation[];
  userTaskDurationTimes: 'idle' | 'work' | 'total';
  showInstanceCount: boolean;
  pointMarkers: boolean;
  precision: number;
  hideRelativeValue: boolean;
  hideAbsoluteValue: boolean;
  alwaysShowRelative: boolean;
  alwaysShowAbsolute: boolean;
  showGradientBars: boolean;
  xml: string;
  tableColumns: ConfigurationTableColumn;
  targetValue: Target_valueSingleReportTargetValueDto;
  heatmapTargetValue: Heatmap_target_valueHeatmapTargetValueDto;
  groupByDateVariableUnit: GroupAggregateByDateUnit;
  distributeByDateVariableUnit: GroupAggregateByDateUnit;
  customBucket: CustomBucket;
  distributeByCustomBucket: CustomBucket;
  sorting?: SortingReportSortingDto | null;
  processPart?: Process_partProcessPartDto | null;
  measureVisualizations: ConfigurationMeasureVisualizationsDto;
  stackedBar: boolean;
  horizontalBar: boolean;
  logScale: boolean;
  yLabel: string;
  xLabel: string;
}

interface SingleReportDataDefinition {
  identifier: string;
  key: string;
  name: string;
  displayName: string;
  versions: string[];
  tenantIds: string[];
}

interface SingleReportData {
  configuration: SingleReportConfiguration;
  definitions: SingleReportDataDefinition[];
}

interface SingleProcessReportData extends SingleReportData {
  filter: ProcessFilter<any>[];
  view: ProcessView;
  groupBy: GroupProcessGroupByDto<any>;
  distributedBy: DistributedBy<any>;
  visualization: ProcessVisualization;
  managementReport: boolean;
  instantPreviewReport: boolean;
  userTaskReport: boolean;
}

interface SingleDecisionReportData extends SingleReportData {
  filter: DecisionFilter<any>[];
  view: DecisionView;
  groupBy: GroupDecisionGroupByDto<any>;
  distributedBy: DistributedBy<any>;
  visualization: DecisionVisualization;
}

interface CombinedReportTargetValue {
  countChart: {
    isBelow: boolean;
    value: string;
  };
  active: boolean;
  durationChart: {
    unit: Target_valueTargetValueUnit;
    isBelow: boolean;
    value: string;
  };
}

interface CombinedReportConfiguration {
  pointMarkers: boolean;
  hideRelativeValue: boolean;
  hideAbsoluteValue: boolean;
  alwaysShowRelative: boolean;
  alwaysShowAbsolute: boolean;
  targetValue: CombinedReportTargetValue;
  yLabel: string;
  xLabel: string;
}

interface CombinedReportData {
  configuration: CombinedReportConfiguration;
  visualization: ProcessVisualization;
  reports: {
    id: string;
    color: string;
  }[];
}

export type GenericReport = Report<
  SingleProcessReportData | SingleDecisionReportData | CombinedReportData
>;

export interface DashboardTile {
  id: string;
  position: {
    x: number;
    y: number;
  };
  dimensions: {
    width: number;
    height: number;
  };
  type: 'optimize_report' | 'external_url' | 'text';
  configuration: {external?: string; text?: SerializedEditorState};
}

export interface Definition {
  identifier: string;
  displayName?: string | JSX.Element[];
  name?: string;
  key?: string;
  tenantIds?: (string | null)[];
  versions?: string[];
  flowNodeIds?: string[];
}

export type Variable = {id?: string; name: string; type: string; label?: string | null};

export interface FilterData {
  value: string | number;
  unit: string;
  operator?: string;
}

type CommonFilter = {includeUndefined?: boolean; excludeUndefined?: boolean; name?: string};

type FixedFilter = {
  type: 'fixed';
  start: string | null;
  end: string | null;
};

type RollingFilter = {
  type: 'rolling' | 'custom';
  start: {value?: number | string; unit?: string} | null;
  end: string | null;
  customNum?: string;
};

type OtherFilter = {
  type: '' | 'relative' | 'today' | 'yesterday' | 'this' | 'last' | 'between' | 'after' | 'before';
  start: {value?: number | string; unit?: string} | null;
  end: string | null;
};

export type Filter = CommonFilter & (FixedFilter | RollingFilter | OtherFilter);

interface CommonFilterState {
  type: string;
  unit: string;
  valid?: boolean;
  startDate: Date | null;
  endDate: Date | null;
  includeUndefined?: boolean;
  excludeUndefined?: boolean;
  applyTo?: Definition[];
  values?: (string | number | boolean | null)[];
  operator?: string;
  customNum: string;
}

export interface NoDateFilterState extends CommonFilterState {
  type: 'this' | 'last' | 'yesterday' | 'today';
  startDate: null;
  endDate: null;
}

export interface BetweenFilterState extends CommonFilterState {
  type: 'between';
  startDate: Date;
  endDate: Date;
}

export interface BeforeFilterState extends CommonFilterState {
  type: 'before';
  startDate: Date | null;
  endDate: Date;
}

export interface AfterFilterState extends CommonFilterState {
  type: 'after';
  startDate: Date;
  endDate: Date | null;
}

export interface CustomFilterState extends CommonFilterState {
  type: 'custom';
}

export type FilterState =
  | CommonFilterState
  | BetweenFilterState
  | BeforeFilterState
  | AfterFilterState
  | CustomFilterState;

export interface AnalysisDurationChartEntry {
  key: number;
  value: number;
  outlier: boolean;
}
