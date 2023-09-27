/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

import instantPreviewDashboard from '../images/instantPreviewDashboard.png';

export function instantPreviewDashboardTemplate() {
  return {
    name: 'instantPreviewDashboard',
    disabled: (definitions: unknown[]) => definitions.length > 1,
    disableDescription: true,
    img: instantPreviewDashboard,
    config: [
      {
        position: {
          x: 3,
          y: 2,
        },
        dimensions: {
          width: 5,
          height: 2,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessOperations_Report1'),
          data: {
            configuration: {
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              userTaskDurationTimes: ['total'],
              sorting: {
                by: 'key',
                order: 'desc',
              },
            },
            filter: [
              {
                type: 'runningInstancesOnly',
                data: null,
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'number',
          },
        },
      },
      {
        position: {
          x: 8,
          y: 2,
        },
        dimensions: {
          width: 5,
          height: 2,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessOperations_Report2'),
          data: {
            configuration: {
              color: '#1991c8',
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              userTaskDurationTimes: ['total'],
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'rolling',
                  start: {
                    value: 7,
                    unit: 'days',
                  },
                  end: null,
                  includeUndefined: false,
                  excludeUndefined: false,
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'number',
          },
        },
      },
      {
        position: {
          x: 13,
          y: 2,
        },
        dimensions: {
          width: 5,
          height: 2,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessOperations_Report3'),
          data: {
            configuration: {
              aggregationTypes: [
                {
                  type: 'sum',
                  value: null,
                },
                {
                  type: 'percentile',
                  value: 75.0,
                },
              ],
              userTaskDurationTimes: ['total'],
              precision: 1,
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {
                    value: 7,
                    unit: 'days',
                  },
                  end: null,
                  includeUndefined: false,
                  excludeUndefined: false,
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'number',
          },
        },
      },
      {
        position: {
          x: 13,
          y: 4,
        },
        dimensions: {
          width: 5,
          height: 2,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessOperations_Report5'),
          data: {
            configuration: {
              color: '#1991c8',
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              userTaskDurationTimes: ['total'],
            },
            filter: [
              {
                type: 'runningInstancesOnly',
                data: null,
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'incident',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'number',
          },
        },
      },
      {
        position: {
          x: 3,
          y: 4,
        },
        dimensions: {
          width: 10,
          height: 3,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessOperations_Report4'),
          data: {
            configuration: {
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              userTaskDurationTimes: ['total'],
            },
            view: {
              entity: 'incident',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'flowNodes',
              value: null,
            },
            visualization: 'heat',
          },
        },
      },
      {
        position: {
          x: 3,
          y: 7,
        },
        dimensions: {
          width: 10,
          height: 2,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessOperations_Report6'),
          data: {
            configuration: {
              color: '#DB3E00',
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              userTaskDurationTimes: ['total'],
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'rolling',
                  start: {
                    value: 7,
                    unit: 'days',
                  },
                  end: null,
                  includeUndefined: false,
                  excludeUndefined: false,
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'startDate',
              value: {
                unit: 'day',
              },
            },
            visualization: 'line',
          },
        },
      },
      {
        position: {
          x: 3,
          y: 10,
        },
        dimensions: {
          width: 10,
          height: 4,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_BusinessReporting_Report1'),
          data: {
            configuration: {
              color: '#1991c8',
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              userTaskDurationTimes: ['total'],
              sorting: {
                by: 'key',
                order: 'asc',
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {
                    value: 1,
                    unit: 'years',
                  },
                  end: null,
                  includeUndefined: false,
                  excludeUndefined: false,
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'endDate',
              value: {
                unit: 'month',
              },
            },
            visualization: 'bar',
          },
        },
      },
      {
        position: {
          x: 3,
          y: 15,
        },
        dimensions: {
          width: 10,
          height: 4,
        },
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.IP_TileText_ProcessImprovement_Report1'),
          data: {
            configuration: {
              aggregationTypes: [
                {
                  type: 'percentile',
                  value: 75.0,
                },
              ],
              userTaskDurationTimes: ['total'],
            },
            view: {
              entity: 'flowNode',
              properties: ['duration'],
            },
            groupBy: {
              type: 'flowNodes',
              value: null,
            },
            visualization: 'heat',
          },
        },
      },
      {
        position: {
          x: 0,
          y: 0,
        },
        dimensions: {
          width: 18,
          height: 1,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: '/external/static/instant_preview_dashboards/OPT_Logo.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Logo.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: null,
            },
          },
        },
      },
      {
        position: {
          x: 0,
          y: 1,
        },
        dimensions: {
          width: 18,
          height: 1,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText:
                        '/external/static/instant_preview_dashboards/OPT_Business_Operations.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Business_Operations.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: null,
            },
          },
        },
      },
      {
        position: {
          x: 0,
          y: 2,
        },
        dimensions: {
          width: 3,
          height: 7,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessOperations'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      altText:
                        '/external/static/instant_preview_dashboards/OPT_Logo_Business_Operations.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Logo_Business_Operations.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: 'ltr',
            },
          },
        },
      },
      {
        position: {
          x: 13,
          y: 6,
        },
        dimensions: {
          width: 5,
          height: 3,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessOperations_Details_1'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 1,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessOperations_Details_2'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessOperations_Details_3'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: 'ltr',
            },
          },
        },
      },
      {
        position: {
          x: 0,
          y: 10,
        },
        dimensions: {
          width: 3,
          height: 4,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessReporting'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      altText:
                        '/external/static/instant_preview_dashboards/OPT_Logo_Business_Reporting.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Logo_Business_Reporting.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: 'ltr',
            },
          },
        },
      },
      {
        position: {
          x: 0,
          y: 9,
        },
        dimensions: {
          width: 18,
          height: 1,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText:
                        '/external/static/instant_preview_dashboards/OPT_Business_Reporting.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Business_Reporting.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: null,
            },
          },
        },
      },
      {
        position: {
          x: 13,
          y: 10,
        },
        dimensions: {
          width: 5,
          height: 4,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessReporting_Details_1'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 1,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessReporting_Details_2'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessReporting_Details_3'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessReporting_Details_4'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_BusinessReporting_Details_5'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: 'ltr',
            },
          },
        },
      },
      {
        position: {
          x: 0,
          y: 14,
        },
        dimensions: {
          width: 18,
          height: 1,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText:
                        '/external/static/instant_preview_dashboards/OPT_Process_Improvement.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Process_Improvement.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: null,
            },
          },
        },
      },
      {
        position: {
          x: 0,
          y: 15,
        },
        dimensions: {
          width: 3,
          height: 4,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_ProcessImprovement'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      altText:
                        '/external/static/instant_preview_dashboards/OPT_Logo_Process_Improvement.png',
                      src: '/external/static/instant_preview_dashboards/OPT_Logo_Process_Improvement.png',
                      width: 0,
                      caption: {
                        editorState: {
                          root: {
                            children: [],
                            indent: 0,
                            format: '',
                            type: 'root',
                            version: 1,
                            direction: null,
                          },
                        },
                      },
                      showCaption: false,
                      type: 'image',
                      version: 1,
                      height: 0,
                      maxWidth: 500,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: null,
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: 'ltr',
            },
          },
        },
      },
      {
        position: {
          x: 13,
          y: 15,
        },
        dimensions: {
          width: 5,
          height: 4,
        },
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_ProcessImprovement_Details_1'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 1,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_ProcessImprovement_Details_2'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_ProcessImprovement_Details_3'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_ProcessImprovement_Details_4'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('instantDashboard.IP_TileText_ProcessImprovement_Details_5'),
                      type: 'text',
                      version: 1,
                    },
                  ],
                  indent: 0,
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
              ],
              indent: 0,
              format: '',
              type: 'root',
              version: 1,
              direction: 'ltr',
            },
          },
        },
      },
    ],
  };
}
