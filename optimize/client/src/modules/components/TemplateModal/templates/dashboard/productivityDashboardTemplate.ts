/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import {getExternalResourcePath} from '../service';

import productivity from './images/productivity.png';

export function productivityDashboardTemplate(generateDocsLink: (path: string) => string) {
  return {
    name: 'productivity',
    disabled: (definitions: unknown[]) => definitions.length > 1,
    img: productivity,
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
                      altText: getExternalResourcePath('pro_input_rate.png', 'templates'),
                      src: getExternalResourcePath('pro_input_rate.png', 'templates'),
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
        position: {x: 12, y: 15},
        dimensions: {width: 6, height: 6},
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
                      text: t('dashboard.templates.productivity_text3_line1'),
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
                      text: t('dashboard.templates.productivity_text3_line2'),
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
                      text: t('dashboard.templates.productivity_text3_line3'),
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
                      text: t('dashboard.templates.productivity_text3_line4'),
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
                          text: t('dashboard.templates.productivity_text3_line5'),
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
                      text: t('dashboard.templates.productivity_text3_line6'),
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
                      text: t('dashboard.templates.productivity_text3_line7'),
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
                          text: t('dashboard.templates.productivity_text3_line8'),
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
                        'components/userguide/process-dashboards/#set-time-and-quality-kpis'
                      ),
                      direction: 'ltr',
                      target: '_blank',
                    },
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.productivity_text3_line9'),
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
                          text: t('dashboard.templates.productivity_text3_line10'),
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
        position: {x: 0, y: 14},
        dimensions: {width: 18, height: 1},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: getExternalResourcePath('pro_completion.png', 'templates'),
                      src: getExternalResourcePath('pro_completion.png', 'templates'),
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
        position: {x: 0, y: 7},
        dimensions: {width: 18, height: 1},
        type: 'text',
        configuration: {
          text: {
            root: {
              children: [
                {
                  children: [
                    {
                      altText: getExternalResourcePath('pro_work_in_progress.png', 'templates'),
                      src: getExternalResourcePath('pro_work_in_progress.png', 'templates'),
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
        position: {x: 0, y: 10},
        dimensions: {width: 12, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_maxNumberOfItemsInProgress'),
          description: t('dashboard.templates.productivity_maxNumberOfItemsInProgress-description'),
          data: {
            configuration: {
              color: '#008B02',
              alwaysShowAbsolute: true,
              targetValue: {
                countChart: {isBelow: true, value: '20'},
                active: true,
              },
              xLabel: t('report.groupBy.runningDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'rolling',
                  start: {value: 1, unit: 'weeks'},
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
              type: 'runningDate',
              value: {unit: 'day'},
            },
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 12, y: 8},
        dimensions: {width: 6, height: 6},
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
                      text: t('dashboard.templates.productivity_text2_line1'),
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
                      text: t('dashboard.templates.productivity_text2_line2'),
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
                      text: t('dashboard.templates.productivity_text2_line3'),
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
                      text: t('dashboard.templates.productivity_text2_line4'),
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
                      text: t('dashboard.templates.productivity_text2_line5'),
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
                          text: t('dashboard.templates.productivity_text2_line6'),
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
                      text: t('dashboard.templates.productivity_text2_line7'),
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
                          text: t('dashboard.templates.productivity_text2_line8'),
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
                        'components/userguide/process-dashboards/#set-time-and-quality-kpis'
                      ),
                      direction: 'ltr',
                      target: '_blank',
                    },
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.productivity_text2_line9'),
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
                          text: t('dashboard.templates.productivity_text2_line10'),
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
        position: {x: 0, y: 3},
        dimensions: {width: 12, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_incomingInLast2Weeks'),
          description: t('dashboard.templates.productivity_incomingInLast2Weeks-description'),
          data: {
            configuration: {
              color: '#008B02',
              alwaysShowAbsolute: true,
              targetValue: {
                countChart: {isBelow: false, value: '10'},
                active: true,
              },
              xLabel: t('report.groupBy.startDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'rolling',
                  start: {value: 2, unit: 'weeks'},
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
              value: {unit: 'day'},
            },
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 12, y: 1},
        dimensions: {width: 6, height: 6},
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
                      text: t('dashboard.templates.productivity_text1_line1'),
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
                      text: t('dashboard.templates.productivity_text1_line2'),
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
                      text: t('dashboard.templates.productivity_text1_line3'),
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
                      text: t('dashboard.templates.productivity_text1_line4'),
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
                      text: t('dashboard.templates.productivity_text1_line5'),
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
                          text: t('dashboard.templates.productivity_text1_line6'),
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
                      text: t('dashboard.templates.productivity_text1_line7'),
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
                          text: t('dashboard.templates.productivity_text1_line8'),
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
                        'components/userguide/process-dashboards/#set-time-and-quality-kpis'
                      ),
                      direction: 'ltr',
                      target: '_blank',
                    },
                    {
                      mode: 'normal',
                      format: 0,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.productivity_text1_line9'),
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
                          text: t('dashboard.templates.productivity_text1_line10'),
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
        position: {x: 0, y: 17},
        dimensions: {width: 12, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_finishedInLast2Weeks'),
          description: t('dashboard.templates.productivity_finishedInLast2Weeks-description'),
          data: {
            configuration: {
              color: '#008B02',
              alwaysShowAbsolute: true,
              targetValue: {
                countChart: {isBelow: false, value: '10'},
                active: true,
              },
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 2, unit: 'weeks'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {type: 'endDate', value: {unit: 'day'}},
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 0, y: 1},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_incomingToday'),
          description: t('dashboard.templates.productivity_incomingToday-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {
                  baseline: '0',
                  target: '10',
                  isBelow: false,
                },
              },
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'relative',
                  start: {value: 0, unit: 'days'},
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
        position: {x: 6, y: 1},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_incomingThisWeek'),
          description: t('dashboard.templates.productivity_incomingThisWeek-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {
                  baseline: '0',
                  target: '50',
                  isBelow: false,
                },
                isKpi: true,
              },
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'relative',
                  start: {value: 0, unit: 'weeks'},
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
        position: {x: 0, y: 8},
        dimensions: {width: 12, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_currentlyInProgress'),
          description: t('dashboard.templates.productivity_currentlyInProgress-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {
                  baseline: '0',
                  target: '20',
                  isBelow: true,
                },
                isKpi: true,
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
            groupBy: {type: 'none', value: null},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 0, y: 15},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_finishedYesterday'),
          description: t('dashboard.templates.productivity_finishedYesterday-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {
                  baseline: '0',
                  target: '10',
                  isBelow: false,
                },
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'relative',
                  start: {value: 1, unit: 'days'},
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
        position: {x: 6, y: 15},
        dimensions: {width: 6, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.productivity_finishedThisWeek'),
          description: t('dashboard.templates.productivity_finishedThisWeek-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {
                  baseline: '0',
                  target: '50',
                  isBelow: false,
                },
                isKpi: true,
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'relative',
                  start: {value: 0, unit: 'weeks'},
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
    ],
  };
}
