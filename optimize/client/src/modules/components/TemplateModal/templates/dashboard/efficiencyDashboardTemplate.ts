/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import {getExternalResourcePath} from '../service';

import efficiency from './images/efficiency.png';

export function efficiencyDashboardTemplate(generateDocsLink: (path: string) => string) {
  return {
    name: 'efficiency',
    disabled: (definitions: unknown[]) => definitions.length > 1,
    img: efficiency,
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
                      altText: getExternalResourcePath('eff_outcome.png', 'templates'),
                      src: getExternalResourcePath('eff_outcome.png', 'templates'),
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
                      altText: getExternalResourcePath('eff_capability.png', 'templates'),
                      src: getExternalResourcePath('eff_capability.png', 'templates'),
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
                      altText: getExternalResourcePath('eff_error_rate.png', 'templates'),
                      src: getExternalResourcePath('eff_error_rate.png', 'templates'),
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
              direction: null,
            },
          },
        },
      },
      {
        position: {x: 8, y: 1},
        dimensions: {width: 6, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_outcomeInLast4Weeks'),
          description: t('dashboard.templates.efficiency_outcomeInLast4Weeks-description'),
          data: {
            configuration: {
              color: '#008B02',
              targetValue: {
                countChart: {isBelow: false, value: '45'},
                active: true,
              },
              sorting: {by: 'key', order: 'asc'},
              stackedBar: true,
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 4, unit: 'weeks'},
                  end: null,
                  includeUndefined: false,
                  excludeUndefined: false,
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'processInstance', properties: ['frequency']},
            groupBy: {type: 'endDate', value: {unit: 'week'}},
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 14, y: 6},
        dimensions: {width: 4, height: 6},
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
                      text: t('dashboard.templates.efficiency_text1_line1'),
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
                      format: 2,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.efficiency_text1_line2'),
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
                      text: t('dashboard.templates.efficiency_text1_line3'),
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
        position: {x: 14, y: 1},
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
                      text: t('dashboard.templates.efficiency_text2_line1'),
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
                      format: 2,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.efficiency_text2_line2'),
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
                      text: t('dashboard.templates.efficiency_text2_line3'),
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
                      format: 2,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.efficiency_text2_line4'),
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
                      text: t('dashboard.templates.efficiency_text2_line5'),
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
                          text: t('dashboard.templates.efficiency_text2_line6'),
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
                      text: t('dashboard.templates.efficiency_text2_line7'),
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
        position: {x: 0, y: 13},
        dimensions: {width: 5, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_typesOfUnwantedOutcomeLastWeek'),
          description: t(
            'dashboard.templates.efficiency_typesOfUnwantedOutcomeLastWeek-description'
          ),
          data: {
            configuration: {
              sorting: {by: 'value', order: 'desc'},
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'rolling',
                  start: {value: 1, unit: 'weeks'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'pie',
          },
        },
      },
      {
        position: {x: 0, y: 8},
        dimensions: {width: 14, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_incidentsInLast3Months'),
          description: t('dashboard.templates.efficiency_incidentsInLast3Months-description'),
          data: {
            configuration: {
              tableColumns: {
                includeNewVariables: true,
                excludedColumns: [],
                includedColumns: [],
                columnOrder: ['Incidents by Flow Node', 'Count', 'Relative Frequency '],
              },
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'rolling',
                  start: {value: 3, unit: 'months'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'incident', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'table',
          },
        },
      },
      {
        position: {x: 5, y: 13},
        dimensions: {width: 9, height: 4},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_processScattering'),
          description: t('dashboard.templates.efficiency_processScattering-description'),
          data: {
            configuration: {
              alwaysShowAbsolute: true,
            },
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
      {
        position: {x: 0, y: 1},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_outcomeThisWeek'),
          description: t('dashboard.templates.efficiency_outcomeThisWeek-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {baseline: '0', target: '50', isBelow: false},
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
            view: {entity: 'processInstance', properties: ['frequency']},
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
          name: t('dashboard.templates.efficiency_unwantedOutcomeThisWeek'),
          description: t('dashboard.templates.efficiency_unwantedOutcomeThisWeek-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {baseline: '0', target: '5', isBelow: true},
                isKpi: false,
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
            groupBy: {type: 'none', value: null},
            view: {entity: 'processInstance', properties: ['percentage']},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 4, y: 3},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_unwantedOutcomeLastWeek'),
          description: t('dashboard.templates.efficiency_unwantedOutcomeLastWeek-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {baseline: '0', target: '5', isBelow: true},
                isKpi: true,
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'relative',
                  start: {value: 1, unit: 'weeks'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            groupBy: {type: 'none', value: null},
            view: {entity: 'processInstance', properties: ['percentage']},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 4, y: 1},
        dimensions: {width: 4, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_outcomeLastWeek'),
          description: t('dashboard.templates.efficiency_outcomeLastWeek-description'),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {baseline: '0', target: '45', isBelow: false},
                isKpi: true,
              },
            },
            filter: [
              {
                type: 'instanceEndDate',
                data: {
                  type: 'relative',
                  start: {value: 1, unit: 'weeks'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            groupBy: {type: 'none', value: null},
            view: {entity: 'processInstance', properties: ['frequency']},
            visualization: 'number',
          },
        },
      },
      {
        position: {x: 14, y: 13},
        dimensions: {width: 4, height: 7},
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
                      text: t('dashboard.templates.efficiency_text3_line1'),
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
                      format: 2,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.efficiency_text3_line2'),
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
                      text: t('dashboard.templates.efficiency_text3_line3'),
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
                      format: 2,
                      style: '',
                      detail: 0,
                      text: t('dashboard.templates.efficiency_text3_line4'),
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
                      text: t('dashboard.templates.efficiency_text3_line5'),
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
                          text: t('dashboard.templates.efficiency_text3_line6'),
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
                      text: t('dashboard.templates.efficiency_text3_line7'),
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
        position: {x: 7, y: 6},
        dimensions: {width: 7, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_nodesCausingIncidentsInLast3Months'),
          description: t(
            'dashboard.templates.efficiency_nodesCausingIncidentsInLast3Months-description'
          ),
          data: {
            configuration: {
              precision: 2,
              alwaysShowAbsolute: true,
            },
            filter: [
              {
                type: 'instanceStartDate',
                data: {
                  type: 'rolling',
                  start: {value: 3, unit: 'months'},
                },
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            view: {entity: 'incident', properties: ['duration']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      },
      {
        position: {x: 0, y: 17},
        dimensions: {width: 14, height: 3},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_typesOfUnwantedOutcomeLas4tWeeks'),
          description: t(
            'dashboard.templates.efficiency_typesOfUnwantedOutcomeLas4tWeeks-description'
          ),
          data: {
            configuration: {
              sorting: {by: 'key', order: 'asc'},
              stackedBar: true,
              xLabel: t('report.groupBy.startDate'),
              yLabel: t('report.view.fn') + ' ' + t('report.view.count'),
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
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'startDate', value: {unit: 'week'}},
            distributedBy: {type: 'flowNode', value: null},
            visualization: 'bar',
          },
        },
      },
      {
        position: {x: 0, y: 6},
        dimensions: {width: 7, height: 2},
        type: 'optimize_report',
        report: {
          name: t('dashboard.templates.efficiency_incidentFreeRateInLast3Months'),
          description: t(
            'dashboard.templates.efficiency_incidentFreeRateInLast3Months-description'
          ),
          data: {
            configuration: {
              targetValue: {
                active: true,
                countProgress: {baseline: '0', target: '95', isBelow: false},
                isKpi: true,
              },
            },
            filter: [
              {
                type: 'doesNotIncludeIncident',
                data: null,
                filterLevel: 'instance',
                appliedTo: ['all'],
              },
            ],
            groupBy: {type: 'none', value: null},
            view: {entity: 'processInstance', properties: ['percentage']},
            visualization: 'number',
          },
        },
      },
    ],
  };
}
