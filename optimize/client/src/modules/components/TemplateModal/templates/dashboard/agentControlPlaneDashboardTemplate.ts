/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

// Placeholder image — replace with an actual screenshot of this dashboard once available
import processDashboard from './images/processDashboard.png';

export function agentControlPlaneDashboardTemplate(_generateDocsLink: (path: string) => string) {
  return {
    name: 'agentControlPlane',
    disabled: (definitions: unknown[]) => definitions.length > 1,
    img: processDashboard,
    config: [
      // ── Row 0-1: KPI tiles ──────────────────────────────────────────────
      // #25 Total runs
      {
        position: {x: 0, y: 0},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_totalRuns'),
          description: t('instantDashboard.report.agentControlPlane_totalRuns-description'),
          data: {
            configuration: {},
            filter: [],
            view: {entity: 'processInstance', properties: ['frequency']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // #32 Incident / failure rate
      {
        position: {x: 4, y: 0},
        dimensions: {width: 5, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_incidentRate'),
          description: t('instantDashboard.report.agentControlPlane_incidentRate-description'),
          data: {
            configuration: {},
            filter: [],
            view: {entity: 'processInstance', properties: ['percentage']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // #30 Avg duration
      {
        position: {x: 9, y: 0},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_avgDuration'),
          description: t('instantDashboard.report.agentControlPlane_avgDuration-description'),
          data: {
            configuration: {aggregationTypes: [{type: 'avg', value: null}]},
            filter: [],
            view: {entity: 'processInstance', properties: ['duration']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // #30 p95 duration
      {
        position: {x: 13, y: 0},
        dimensions: {width: 5, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_p95Duration'),
          description: t('instantDashboard.report.agentControlPlane_p95Duration-description'),
          data: {
            configuration: {aggregationTypes: [{type: 'percentile', value: 95}]},
            filter: [],
            view: {entity: 'processInstance', properties: ['duration']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // ── Row 2-5: Duration trend (#31) ────────────────────────────────────
      {
        position: {x: 0, y: 2},
        dimensions: {width: 18, height: 4},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_durationTrend'),
          description: t('instantDashboard.report.agentControlPlane_durationTrend-description'),
          data: {
            configuration: {
              sorting: {by: 'key', order: 'asc'},
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.duration'),
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {type: 'rolling', start: {value: 8, unit: 'weeks'}},
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'processInstance', properties: ['duration']},
            groupBy: {type: 'endDate', value: {unit: 'week'}},
            visualization: 'line',
          },
        },
      },
      // ── Row 6-7: Token KPI tiles (#27, #28) ─────────────────────────────
      // #27 Avg tokens per run
      {
        position: {x: 0, y: 6},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_avgTokensPerRun'),
          description: t(
            'instantDashboard.report.agentControlPlane_avgTokensPerRun-description'
          ),
          data: {
            configuration: {aggregationTypes: [{type: 'avg', value: null}]},
            filter: [],
            view: {entity: 'processInstance', properties: ['tokens']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // #27 Median tokens per run (p50)
      {
        position: {x: 6, y: 6},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_medianTokensPerRun'),
          description: t(
            'instantDashboard.report.agentControlPlane_medianTokensPerRun-description'
          ),
          data: {
            configuration: {aggregationTypes: [{type: 'percentile', value: 50}]},
            filter: [],
            view: {entity: 'processInstance', properties: ['tokens']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // #28 Token outlier bands (p5 / p95)
      {
        position: {x: 12, y: 6},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_tokenOutlierBands'),
          description: t(
            'instantDashboard.report.agentControlPlane_tokenOutlierBands-description'
          ),
          data: {
            configuration: {
              aggregationTypes: [
                {type: 'percentile', value: 5},
                {type: 'percentile', value: 95},
              ],
            },
            filter: [],
            view: {entity: 'processInstance', properties: ['tokens']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      // ── Row 8-11: Token trend over time (#29) ────────────────────────────
      {
        position: {x: 0, y: 8},
        dimensions: {width: 18, height: 4},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_tokenTrend'),
          description: t('instantDashboard.report.agentControlPlane_tokenTrend-description'),
          data: {
            configuration: {
              sorting: {by: 'key', order: 'asc'},
              xLabel: t('report.groupBy.endDate'),
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {type: 'rolling', start: {value: 8, unit: 'weeks'}},
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'processInstance', properties: ['tokens']},
            groupBy: {type: 'endDate', value: {unit: 'week'}},
            visualization: 'line',
          },
        },
      },
      // ── Row 12-16: Tool call frequency / distribution (#33) ──────────────
      {
        position: {x: 0, y: 12},
        dimensions: {width: 18, height: 5},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.agentControlPlane_toolCallDistribution'),
          description: t(
            'instantDashboard.report.agentControlPlane_toolCallDistribution-description'
          ),
          data: {
            configuration: {alwaysShowAbsolute: true},
            filter: [
              {
                type: 'instanceEndDate',
                data: {type: 'rolling', start: {value: 8, unit: 'weeks'}},
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
    ],
  };
}
