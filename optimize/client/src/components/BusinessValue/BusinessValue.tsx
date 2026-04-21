/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Column, Grid} from '@carbon/react';

import {Loading, PageTitle} from 'components';
import {showError} from 'notifications';
import {withErrorHandling, WithErrorHandlingProps} from 'HOC';

import {loadSummary} from './service';
import {BusinessValueSummary} from './types';
import AutomationDistributionChart from './AutomationDistributionChart';
import CostByModelChart from './CostByModelChart';
import ProcessCostBreakdownChart from './ProcessCostBreakdownChart';
import ProcessValueChart from './ProcessValueChart';
import TopAgentTasksChart from './TopAgentTasksChart';
import ValueTrendChart from './ValueTrendChart';
import TopProcessesTable from './TopProcessesTable';

import './BusinessValue.scss';

function BusinessValue({mightFail}: WithErrorHandlingProps) {
  const [data, setData] = useState<BusinessValueSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    mightFail(loadSummary(), setData, showError, () => setLoading(false));
  }, [mightFail]);

  if (loading) {
    return <Loading />;
  }

  if (!data) {
    return null;
  }

  const {kpis} = data;

  return (
    <Grid condensed className="BusinessValue" fullWidth>
      <Column sm={4} md={8} lg={16}>
        <PageTitle pageName="Business Value Dashboard" />
        <h1 className="businessValueDashboard">Business Value Dashboard</h1>
      </Column>

      {/* Row 1 – 6 KPI number tiles */}
      <Column sm={4} md={8} lg={16}>
        <div className="kpiRow">
          <div className="tile kpiTile">
            <div className="tileTitle">Active Processes</div>
            <div className="tileNumber">{kpis.activeProcesses}</div>
          </div>
          <div className="tile kpiTile">
            <div className="tileTitle">Instances Completed</div>
            <div className="tileNumber">{kpis.completedInstances.toLocaleString()}</div>
          </div>
          <div className="tile kpiTile">
            <div className="tileTitle">Value Created</div>
            <div className="tileNumber">€ {kpis.totalValueCreated.toLocaleString()}</div>
          </div>
          <div className="tile kpiTile">
            <div className="tileTitle">Total Cost</div>
            <div className="tileNumber">€ {kpis.totalCost.toLocaleString()}</div>
          </div>
          <div className="tile kpiTile">
            <div className="tileTitle">Automation Rate</div>
            <div className="tileNumber">{Math.round(kpis.automationRate * 100)}%</div>
          </div>
          <div className="tile kpiTile">
            <div className="tileTitle">Platform ROI</div>
            <div className="tileNumber">{kpis.platformRoi}x</div>
          </div>
        </div>
      </Column>

      {/* Row 2 – Automation Distribution & Value Trend */}
      <Column sm={4} md={8} lg={16}>
        <div className="chartRow">
          <div className="tile chartTile">
            <div className="tileTitle">Automation Distribution</div>
            <div className="tileContent">
              <AutomationDistributionChart
                humanTaskCount={kpis.humanTaskCount}
                agentTaskCount={kpis.agentTaskCount}
                autoTaskCount={kpis.autoTaskCount}
              />
            </div>
          </div>
          <div className="tile chartTile">
            <div className="tileTitle">Automation Value Trend</div>
            <div className="tileContent">
              <ValueTrendChart trend={data.trend} />
            </div>
          </div>
        </div>
      </Column>

      {/* Row 3 – Top Processes & AI Usage */}
      <Column sm={4} md={8} lg={16}>
        <div className="fullRow">
          <div className="tile chartTile">
            <div className="tileTitle">Top Processes by Business Value</div>
            <div className="tileContent">
              <TopProcessesTable processes={data.topProcesses} />
              <div className="processChartsRow">
                <div className="processChartSection">
                  <div className="aiSectionLabel">Value Comparison</div>
                  <ProcessValueChart processes={data.topProcesses} />
                </div>
                <div className="processChartSection">
                  <div className="aiSectionLabel">Cost Breakdown</div>
                  <ProcessCostBreakdownChart processes={data.topProcesses} />
                </div>
              </div>
            </div>
          </div>
        </div>
      </Column>

      {/* Row 4 – AI Usage & Cost */}
      <Column sm={4} md={8} lg={16}>
        <div className="fullRow">
          <div className="tile chartTile">
            <div className="tileTitle">AI Usage & Cost</div>
            <div className="tileContent">
              <div className="aiLayout">
                <div className="aiMetrics aiMetricsLeft">
                  <div className="aiMetric">
                    <span className="aiMetricLabel">Total LLM Cost</span>
                    <span className="aiMetricValue">€{kpis.totalLlmCost.toLocaleString()}</span>
                  </div>
                  <div className="aiMetric">
                    <span className="aiMetricLabel">AI Agent Tasks</span>
                    <span className="aiMetricValue">{kpis.agentTaskCount.toLocaleString()}</span>
                  </div>
                </div>
                <div className="aiChartSection aiChartCostByModel">
                  <div className="aiSectionLabel">Usage Cost by Model</div>
                  <CostByModelChart costByModel={data.costByModel} />
                </div>
                <div className="aiChartSection aiChartAgentTasks">
                  <div className="aiSectionLabel">Top Agent Tasks by Cost</div>
                  <TopAgentTasksChart agentTasks={data.topAgentTasks} />
                </div>
                <div className="aiMetrics aiMetricsRight">
                  <div className="aiMetric">
                    <span className="aiMetricLabel">AI Share of Cost</span>
                    <span className="aiMetricValue">
                      {kpis.totalCost > 0
                        ? `${Math.round((kpis.totalLlmCost / kpis.totalCost) * 100)}%`
                        : '—'}
                    </span>
                    <span className="aiMetricSub">of total cost</span>
                  </div>
                  <div className="aiMetric">
                    <span className="aiMetricLabel">Value per AI Dollar</span>
                    <span className="aiMetricValue">
                      {kpis.totalLlmCost > 0
                        ? `${(kpis.totalValueCreated / kpis.totalLlmCost).toFixed(1)}x`
                        : '—'}
                    </span>
                    <span className="aiMetricSub">return on AI spend</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Column>
    </Grid>
  );
}

export default withErrorHandling(BusinessValue);
