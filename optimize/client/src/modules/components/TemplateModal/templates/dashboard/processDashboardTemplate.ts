/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import {getExternalResourcePath} from '../service';

import processDashboard from './images/processDashboard.png';

export function processDashboardTemplate(generateDocsLink: (path: string) => string) {
  return {
    name: 'processDashboard',
    disabled: (definitions: unknown[]) => definitions.length > 1,
    img: processDashboard,
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
                      altText: getExternalResourcePath(
                        'prod_business_reporting.png',
                        'instant_preview_dashboards'
                      ),
                      src: getExternalResourcePath(
                        'prod_business_reporting.png',
                        'instant_preview_dashboards'
                      ),
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
        position: {x: 5, y: 6},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_currentlyInProgress'),
          description: t(
            'instantDashboard.report.processDashboard_currentlyInProgress-description'
          ),
          data: {
            configuration: {},
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
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 0, y: 1},
        dimensions: {width: 18, height: 5},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_currentlyRunningProcesses'),
          description: t(
            'instantDashboard.report.processDashboard_currentlyRunningProcesses-description'
          ),
          data: {
            configuration: {
              alwaysShowAbsolute: true,
            },
            filter: [
              {
                type: 'runningFlowNodesOnly',
                data: null,
                filterLevel: 'view',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
      {
        position: {x: 4, y: 8},
        dimensions: {width: 14, height: 4},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_currentlyOpenIncidents'),
          description: t(
            'instantDashboard.report.processDashboard_currentlyOpenIncidents-description'
          ),
          data: {
            configuration: {
              alwaysShowAbsolute: true,
            },
            filter: [
              {
                type: 'includesOpenIncident',
                filterLevel: 'view',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'incident', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
      {
        position: {x: 0, y: 6},
        dimensions: {width: 5, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_incomingInLast7Days'),
          description: t(
            'instantDashboard.report.processDashboard_incomingInLast7Days-description'
          ),
          data: {
            configuration: {},
            filter: [
              {
                type: 'instanceStartDate',
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
              properties: ['frequency'],
            },
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 9, y: 6},
        dimensions: {width: 5, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_endedInLast7Days'),
          description: t('instantDashboard.report.processDashboard_endedInLast7Days-description'),
          data: {
            configuration: {},
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
              properties: ['frequency'],
            },
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 0, y: 12},
        dimensions: {width: 18, height: 1},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: getExternalResourcePath(
                        'prod_business_operations.png',
                        'instant_preview_dashboards'
                      ),
                      src: getExternalResourcePath(
                        'prod_business_operations.png',
                        'instant_preview_dashboards'
                      ),
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
        position: {x: 0, y: 17},
        dimensions: {width: 18, height: 1},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: getExternalResourcePath(
                        'prod_process_improvement.png',
                        'instant_preview_dashboards'
                      ),
                      src: getExternalResourcePath(
                        'prod_process_improvement.png',
                        'instant_preview_dashboards'
                      ),
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
        position: {x: 4, y: 13},
        dimensions: {width: 14, height: 4},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_processEndedGroupedByMonth'),
          description: t(
            'instantDashboard.report.processDashboard_processEndedGroupedByMonth-description'
          ),
          data: {
            configuration: {
              sorting: {by: 'key', order: 'asc'},
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 6, unit: 'months'},
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
              value: {unit: 'month'},
            },
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 0, y: 13},
        dimensions: {width: 4, height: 4},
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
                      text: t('instantDashboard.processDashboard_text1_line1'),
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
                      text: t('instantDashboard.processDashboard_text1_line2'),
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
                      children: [
                        {
                          mode: 'normal',
                          format: 0,
                          style: '',
                          detail: 0,
                          text: t('instantDashboard.processDashboard_text1_line3'),
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
                        'components/userguide/process-dashboards/#configuring-process-owner-and-digests'
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
      {
        position: {x: 0, y: 18},
        dimensions: {width: 4, height: 5},
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
                      text: t('instantDashboard.processDashboard_text2_line1'),
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
                      text: t('instantDashboard.processDashboard_text2_line2'),
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
                  direction: null,
                },
                {
                  children: [
                    {
                      children: [
                        {
                          mode: 'normal',
                          format: 0,
                          style: '',
                          detail: 0,
                          text: t('instantDashboard.processDashboard_text2_line3'),
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
                        'components/userguide/process-analysis/outlier-analysis/'
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
      {
        position: {x: 0, y: 8},
        dimensions: {width: 4, height: 4},
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
                      text: t('instantDashboard.processDashboard_text3_line1'),
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
                      text: t('instantDashboard.processDashboard_text3_line2'),
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
                      children: [
                        {
                          mode: 'normal',
                          format: 0,
                          style: '',
                          detail: 0,
                          text: t('instantDashboard.processDashboard_text3_line3'),
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
                        'components/userguide/creating-reports/#creating-a-single-report'
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
      {
        position: {x: 14, y: 6},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_openIncidents'),
          description: t('instantDashboard.report.processDashboard_openIncidents-description'),
          data: {
            configuration: {},
            filter: [
              {
                type: 'includesOpenIncident',
                data: null,
                filterLevel: 'view',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'incident', properties: ['frequency']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 4, y: 18},
        dimensions: {width: 14, height: 5},
        type: 'optimize_report',
        report: {
          name: t('instantDashboard.report.processDashboard_bottlenecksInTheProcess'),
          description: t(
            'instantDashboard.report.processDashboard_bottlenecksInTheProcess-description'
          ),
          data: {
            configuration: {},
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 6, unit: 'months'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'flowNode', properties: ['duration']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
    ],
  };
}
