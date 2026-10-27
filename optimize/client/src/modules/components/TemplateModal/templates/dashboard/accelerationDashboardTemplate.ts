/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import {getExternalResourcePath} from '../service';

import acceleration from './images/acceleration.png';

export function accelerationDashboardTemplate(generateDocsLink: (path: string) => string) {
  return {
    name: 'acceleration',
    disabled: (definitions: unknown[]) => definitions.length > 1,
    img: acceleration,
    config: [
      {
        position: {x: 0, y: 0},
        dimensions: {width: 18, height: 1},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: getExternalResourcePath('acc_cycle_time.png', 'templates'),
                      src: getExternalResourcePath('acc_cycle_time.png', 'templates'),
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
        position: {x: 0, y: 5},
        dimensions: {width: 18, height: 1},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: getExternalResourcePath('acc_task_duration.png', 'templates'),
                      src: getExternalResourcePath('acc_task_duration.png', 'templates'),
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
        position: {x: 4, y: 1},
        dimensions: {width: 9, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.acceleration_cycleTimeLastWeeks'),
          description: t('dashboard.templates.acceleration_cycleTimeLastWeeks-description'),
          data: {
            configuration: {
              color: '#008B02',
              precision: 2,
              alwaysShowAbsolute: true,
              targetValue: {
                active: true,
                durationChart: {
                  unit: 'hours',
                  isBelow: true,
                  value: '60',
                },
                isKpi: null,
              },
              sorting: {by: 'key', order: 'asc'},
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.duration'),
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 8, unit: 'weeks'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['duration'],
            },
            groupBy: {type: 'endDate', value: {unit: 'week'}},
            visualization: 'line',
          },
        },
      },
      {
        position: {x: 13, y: 1},
        dimensions: {width: 5, height: 4},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 1,
                      style: 'font-size: 20px;',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text1_line1'),
                      type: 'text',
                      version: 1,
                    },
                    {type: 'linebreak', version: 1},
                  ],
                  format: '',
                  type: 'paragraph',
                  version: 1,
                  direction: 'ltr',
                },
                {
                  children: [
                    {
                      altText: getExternalResourcePath('Target.png', 'templates'),
                      src: getExternalResourcePath('Target.png', 'templates'),
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
                    {
                      mode: 'normal',
                      format: 3,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text1_line2'),
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
                      text: t('dashboard.templates.acceleration_text1_line3'),
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
                      altText: getExternalResourcePath('customize.png', 'templates'),
                      src: getExternalResourcePath('customize.png', 'templates'),
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
                    {
                      mode: 'normal',
                      format: 3,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text1_line4'),
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
                      text: '- ',
                      type: 'text',
                      version: 1,
                    },
                    {
                      children: [
                        {
                          mode: 'normal',
                          format: 0,
                          style: '',
                          detail: 0,
                          text: t('dashboard.templates.acceleration_text1_line5'),
                          type: 'text',
                          version: 1,
                        },
                      ],
                      indent: 0,
                      format: '',
                      rel: 'noopener norefereer',
                      type: 'link',
                      version: 1,
                      url: generateDocsLink(
                        'components/userguide/process-analysis/report-analysis/configure-reports/#chart-goal-line'
                      ),
                      direction: 'ltr',
                      target: '_blank',
                    },
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text1_line6'),
                      type: 'text',
                      version: 1,
                    },
                    {
                      mode: 'normal',
                      format: 1,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text1_line7'),
                      type: 'text',
                      version: 1,
                    },
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: ' .',
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
                      text: t('dashboard.templates.acceleration_text1_line8'),
                      type: 'text',
                      version: 1,
                    },
                    {
                      children: [
                        {
                          mode: 'normal',
                          format: 0,
                          style: '',
                          detail: 0,
                          text: t('dashboard.templates.acceleration_text1_line9'),
                          type: 'text',
                          version: 1,
                        },
                      ],
                      indent: 0,
                      format: '',
                      rel: 'noopener norefereer',
                      type: 'link',
                      version: 1,
                      url: generateDocsLink(
                        'components/userguide/process-analysis/flow-node-filters/#flow-node-selection'
                      ),
                      direction: 'ltr',
                      target: '_blank',
                    },
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text1_line10'),
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
        position: {x: 7, y: 14},
        dimensions: {width: 11, height: 3},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 1,
                      style: 'font-size: 20px;',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text2_line1'),
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
                    {type: 'linebreak', version: 1},
                    {
                      altText: getExternalResourcePath('Target.png', 'templates'),
                      src: getExternalResourcePath('Target.png', 'templates'),
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
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: ' ',
                      type: 'text',
                      version: 1,
                    },
                    {
                      mode: 'normal',
                      format: 3,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text2_line2'),
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
                      text: t('dashboard.templates.acceleration_text2_line3'),
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
        position: {x: 0, y: 1},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.acceleration_cycleTimeLast7Days'),
          description: t('dashboard.templates.acceleration_cycleTimeLast7Days-description'),
          data: {
            configuration: {
              precision: 2,
              targetValue: {
                durationProgress: {
                  baseline: {unit: 'hours', value: '0'},
                  target: {
                    unit: 'days',
                    value: '2',
                    isBelow: true,
                  },
                },
                active: true,
                isKpi: true,
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 7, unit: 'days'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['duration'],
            },
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 0, y: 3},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.acceleration_cycleTimeLast4Weeks'),
          description: t('dashboard.templates.acceleration_cycleTimeLast4Weeks-description'),
          data: {
            configuration: {
              precision: 2,
              targetValue: {
                durationProgress: {
                  baseline: {unit: 'hours', value: '0'},
                  target: {
                    unit: 'days',
                    value: '2',
                    isBelow: true,
                  },
                },
                active: true,
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 4, unit: 'weeks'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['duration'],
            },
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 5, y: 6},
        dimensions: {width: 13, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.acceleration_deviationOfPlannedToAverageDuration'),
          description: t(
            'dashboard.templates.acceleration_deviationOfPlannedToAverageDuration-description'
          ),
          data: {
            configuration: {},
            view: {entity: 'flowNode', properties: ['duration']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
      {
        position: {x: 0, y: 10},
        dimensions: {width: 7, height: 7},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.acceleration_durationOfCriticalTasksInLastMonth'),
          description: t(
            'dashboard.templates.acceleration_durationOfCriticalTasksInLastMonth-description'
          ),
          data: {
            configuration: {
              aggregationTypes: [
                {type: 'min', value: null},
                {type: 'max', value: null},
                {type: 'percentile', value: 95},
              ],
              sorting: {by: 'value', order: 'desc'},
              horizontalBar: true,
              logScale: true,
              xLabel: t('report.groupBy.flowNodes'),
              yLabel: t('report.view.fn') + ' ' + t('report.view.duration'),
            },
            filter: [
              {
                type: 'doesNotIncludeIncident',
                data: null,
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 1, unit: 'months'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'flowNode', properties: ['duration']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 7, y: 10},
        dimensions: {width: 11, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.acceleration_taskBottlenecks'),
          description: t('dashboard.templates.acceleration_taskBottlenecks-description'),
          data: {
            configuration: {},
            view: {entity: 'flowNode', properties: ['duration']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
      {
        position: {x: 0, y: 6},
        dimensions: {width: 5, height: 4},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      mode: 'normal',
                      format: 1,
                      style: 'font-size: 20px;',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text3_line1'),
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
                  direction: null,
                },
                {
                  children: [
                    {
                      altText: getExternalResourcePath('customize.png', 'templates'),
                      src: getExternalResourcePath('customize.png', 'templates'),
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
                    {
                      mode: 'normal',
                      format: 3,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.acceleration_text3_line2'),
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
                      text: t('dashboard.templates.acceleration_text3_line3'),
                      type: 'text',
                      version: 1,
                    },
                    {
                      children: [
                        {
                          mode: 'normal',
                          format: 0,
                          style: '',
                          detail: 0,
                          text: t('dashboard.templates.acceleration_text3_line4'),
                          type: 'text',
                          version: 1,
                        },
                      ],
                      indent: 0,
                      format: '',
                      rel: 'noopener norefereer',
                      type: 'link',
                      version: 1,
                      url: generateDocsLink(
                        'components/userguide/process-analysis/report-analysis/compare-target-values/'
                      ),
                      direction: 'ltr',
                      target: '_blank',
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
