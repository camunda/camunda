/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rgba} from 'polished';

import darkZeebraStripe from 'modules/components/ZeebraStripe/tree-view-bg-dark.png';
import lightZeebraStripe from 'modules/components/ZeebraStripe/tree-view-bg-light.png';
import incidentsOverlayDarkBackgroundImage from './images/bg-dark@2x.png';
import incidentsOverlayLightBackgroundImage from './images/bg-light@2x.png';

const SEMANTIC_COLORS = {
  selections: '#4d90ff',
  allIsWell: '#10d070',
  incidentsAndErrors: '#ff3d3d',
  filtersAndWarnings: '#ffa533',
  focusOuter: '#8cb7ff',
  badge01: '#d7d7d9',
  badge02: '#88888d',
  primaryButton01: '#a2c5ff',
  primaryButton02: '#80b0ff',
  primaryButton03: '#3c85ff',
  primaryButton04: '#1a70ff',
  primaryButton05: '#005df7',
  black: '#000',
  white: '#fff',
  grey: '#dedede',
  outlineError: '#ffafaf',
  transparent: 'transparent',
  shimmer: '#82b1ff',
  supportWarning: '#f1c21b',
  borderStrong01: '#8d8d8d',
} as const;
const DARK_COLORS = {
  ...SEMANTIC_COLORS,
  ui01: '#1c1f23',
  ui02: '#313238',
  ui03: '#393a41',
  ui04: '#45464e',
  ui05: '#5b5e63',
  ui06: '#6d7076',
  itemOdd: '#313238',
  itemEven: '#37383e',
  selectedOdd: '#3a527d',
  selectedEven: '#3e5681',
  menuActive: '#393a42',
  linkDefault: '#d9eaff',
  linkHover: '#c8e1ff',
  linkActive: '#eaf3ff',
  linkVisited: '#c889fe',
  focusInner: '#2B7BFF',
  button01: '#6b6f74',
  button02: '#7f8289',
  button03: '#34353a',
  button04: '#3c3d43',
  button05: '#646670',
  button06: '#2c2d31',
  button07: '#73777e',
  label: '#4a4c51',
  pillHover: '#767a80',
  rowHover: '#4e4f55',
  logo: '#f8f8f8',
  canceledBadgeActive: '#6f897d',
  canceledBadgeIncident: '#94595b',
  warningNotification: '#704000',
  errorNotification: '#7a0000',
} as const;

const LIGHT_COLORS = {
  ...SEMANTIC_COLORS,
  ui01: '#f2f3f5',
  ui02: '#f7f8fa',
  ui03: '#b0bac7',
  ui04: '#fdfdfe',
  ui05: '#d8dce3',
  ui06: '#62626e',
  itemOdd: '#fdfdfe',
  itemEven: '#f9fafc',
  selectedOdd: '#bfd6fe',
  selectedEven: '#bdd4fd',
  menuActive: '#bcc6d2',
  linkDefault: '#346ac4',
  linkHover: '#4b7ccf',
  linkActive: '#29549c',
  linkVisited: '#a846fe',
  focusInner: '#c8e1ff',
  button01: '#cdd4df',
  button02: '#9ea9b7',
  button03: '#88889a',
  button04: '#f1f2f5',
  button05: '#e7e9ee',
  button06: '#d3d6e0',
  label: '#edeff3',
  rowHover: '#e7e9ee',
  logo: '#666666',
  canceledBadgeActive: '#96baa8',
  canceledBadgeIncident: '#cc8a8a',
  warningNotification: '#fff0db',
  errorNotification: '#ffe0e0',
} as const;

const theme = {
  dark: {
    cmTheme: 'Dark',
    colors: {
      ...DARK_COLORS,
      text01: SEMANTIC_COLORS.white,
      text02: SEMANTIC_COLORS.white,
      borderColor: DARK_COLORS.ui04,
      layout: {
        dashboard: {
          backgroundColor: DARK_COLORS.ui01,
        },
        default: {
          backgroundColor: DARK_COLORS.ui03,
        },
      },
      decisionInstance: {
        header: {
          backgroundColor: DARK_COLORS.ui03,
        },
        backgroundColor: DARK_COLORS.ui02,
      },
      metricPanel: {
        skeletonBar: {
          backgroundColor: SEMANTIC_COLORS.badge02,
        },
        color: rgba(SEMANTIC_COLORS.white, 0.9),
      },
      panelListItem: {
        active: {
          borderColor: rgba(DARK_COLORS.ui05, 0.7),
        },
        hover: {
          borderColor: DARK_COLORS.ui05,
        },
      },
      dashboard: {
        backgroundColor: DARK_COLORS.button06,
        panelStyles: {
          backgroundColor: DARK_COLORS.ui03,
        },
        metricPanelWrapper: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
        },
        skeleton: {
          block: {
            backgroundColor: SEMANTIC_COLORS.badge02,
          },
        },
        message: {
          default: {
            color: rgba(SEMANTIC_COLORS.white, 0.8),
          },
          error: {
            color: rgba(SEMANTIC_COLORS.incidentsAndErrors, 0.9),
          },
          success: {
            color: rgba(SEMANTIC_COLORS.allIsWell, 0.9),
          },
        },
      },
      header: {
        separator: rgba(SEMANTIC_COLORS.white, 0.5),
        user: {
          backgroundColor: rgba(SEMANTIC_COLORS.badge02, 0.2),
        },
        details: {
          borderColor: rgba('#f6fcfb', 0.5),
        },
        skeleton: {
          backgroundColor: rgba(SEMANTIC_COLORS.badge02, 0.2),
        },
        license: {
          backgroundColor: DARK_COLORS.ui04,
          borderColor: DARK_COLORS.ui06,
          arrowStyle: {
            before: {
              borderColor: DARK_COLORS.ui06,
            },
            after: {
              borderColor: DARK_COLORS.ui04,
            },
          },
        },
      },
      variablesPanel: {
        footer: {
          backgroundColor: DARK_COLORS.ui05,
        },
        ioMappings: {
          banner: {
            backgroundColor: DARK_COLORS.selectedOdd,
            closeIconColor: SEMANTIC_COLORS.white,
          },
        },
      },
      variables: {
        skeleton: {
          backgroundColor: DARK_COLORS.ui02,
        },
        placeholder: {
          color: SEMANTIC_COLORS.grey,
        },
        tHead: {
          backgroundColor: DARK_COLORS.ui02,
        },
        editButton: {
          disabled: {
            color: LIGHT_COLORS.ui02,
          },
        },
        icons: {
          color: LIGHT_COLORS.ui02,
        },
        variablesTable: {
          tr: {
            backgroundColor: DARK_COLORS.ui05,
          },
        },
        backdrop: {
          backgroundColor: rgba(DARK_COLORS.ui02, 0.75),
          spinner: {
            borderColor: SEMANTIC_COLORS.white,
          },
        },
        color: rgba(SEMANTIC_COLORS.white, 0.8),
      },
      flowNodeInstanceLog: {
        color: rgba(SEMANTIC_COLORS.white, 0.9),
      },
      flowNodeInstancesTree: {
        bar: {
          nodeName: {
            selected: {
              borderColor: rgba(SEMANTIC_COLORS.white, 0.25),
            },
          },
          placeholder: {
            modificationIcon: {
              color: DARK_COLORS.linkDefault,
            },
          },
        },
        foldable: {
          summaryLabel: {
            backgroundColor: DARK_COLORS.ui04,
          },
        },
        timeStampLabel: {
          backgroundColor: rgba(LIGHT_COLORS.ui02, 0.15),
        },
        connectionDot: {
          color: '#65666d',
        },
        nodeDetails: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          selected: {
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
        },
        ul: {
          backgroundColor: '#65666d',
        },
      },
      incidentsFilter: {
        filtersWrapper: {
          backgroundColor: DARK_COLORS.ui03,
        },
        label: {
          backgroundColor: DARK_COLORS.ui04,
        },
        moreDropdown: {
          dropdownToggle: {
            borderColor: DARK_COLORS.ui06,
            color: DARK_COLORS.ui02,
          },
          item: {
            borderColor: DARK_COLORS.ui04,
          },
        },
        buttonsWrapper: {
          backgroundColor: DARK_COLORS.ui04,
        },
      },
      incidentsOverlay: {
        backgroundColor: DARK_COLORS.ui02,
      },
      instanceHeader: {
        backgroundColor: DARK_COLORS.ui02,
      },
      topPanel: {
        backgroundColor: DARK_COLORS.ui02,
        instanceHeader: {
          backgroundColor: DARK_COLORS.ui03,
        },
        modificationInfoBanner: {
          backgroundColor: SEMANTIC_COLORS.supportWarning,
          separatorColor: SEMANTIC_COLORS.borderStrong01,
          color: DARK_COLORS.ui04,
          linkButton: {
            default: LIGHT_COLORS.linkDefault,
            hover: LIGHT_COLORS.linkHover,
            active: LIGHT_COLORS.linkActive,
          },
        },
      },
      emptyMessage: {
        color: SEMANTIC_COLORS.grey,
      },
      emptyState: {
        color: SEMANTIC_COLORS.grey,
      },
      decisionsList: {
        backgroundColor: DARK_COLORS.ui02,
        header: {
          backgroundColor: DARK_COLORS.ui03,
        },
      },
      list: {
        backgroundColor: DARK_COLORS.ui02,
      },
      createOperationDropdown: {
        dropdownButtonStyles: {
          color: LIGHT_COLORS.ui04,
        },
      },
      operationsEntry: {
        entry: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          isRunning: {
            backgroundColor: DARK_COLORS.ui03,
          },
        },
        iconStyle: {
          color: SEMANTIC_COLORS.white,
        },
      },
      operationsPanel: {
        skeleton: {
          entry: {
            backgroundColor: DARK_COLORS.ui03,
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
        },
        emptyMessage: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          backgroundColor: DARK_COLORS.ui03,
        },
      },
      operationsProgressBar: {
        shimmerColor: SEMANTIC_COLORS.shimmer,
      },
      processInstance: {
        modifications: {
          footer: {
            backgroundColor: DARK_COLORS.ui03,
            lastModification: {
              backgroundColor: DARK_COLORS.ui05,
              buttonColor: DARK_COLORS.linkDefault,
              separatorColor: DARK_COLORS.ui06,
              color: SEMANTIC_COLORS.white,
            },
          },
          helperModal: {
            modificationType: {
              color: DARK_COLORS.linkDefault,
            },
          },
          summaryModal: {
            tableHeader: {
              backgroundColor: DARK_COLORS.ui01,
            },
          },
          loadingOverlay: {
            backgroundColor: rgba(SEMANTIC_COLORS.black, 0.65),
            spinner: {
              borderColor: SEMANTIC_COLORS.white,
            },
          },
        },
      },
      sortableTable: {
        backgroundColor: DARK_COLORS.ui02,
        hover: DARK_COLORS.rowHover,
        header: {
          tr: {
            backgroundColor: DARK_COLORS.ui03,
          },
        },
        tr: {
          selected: {
            backgroundColor: DARK_COLORS.selectedOdd,
          },
        },
      },
      dataTable: {
        backgroundColor: DARK_COLORS.ui02,
        header: {
          tr: {
            backgroundColor: DARK_COLORS.ui02,
          },
        },
      },
      filtersPanel: {
        modalIcon: {
          color: LIGHT_COLORS.ui02,
        },
      },
      disclaimer: {
        container: {
          color: rgba(SEMANTIC_COLORS.white, 0.7),
        },
      },
      login: {
        logo: {
          color: SEMANTIC_COLORS.white,
        },
        loginTitle: {
          color: SEMANTIC_COLORS.white,
        },
        input: {
          backgroundColor: DARK_COLORS.ui02,
          borderColor: DARK_COLORS.button02,
          labelColor: SEMANTIC_COLORS.white,
          focusInner: DARK_COLORS.focusInner,
        },
      },
      decisionViewer: {
        background: DARK_COLORS.ui02,
        text: SEMANTIC_COLORS.white,
        border: SEMANTIC_COLORS.grey,
        highlightedRow: {
          background: DARK_COLORS.selectedOdd,
          color: SEMANTIC_COLORS.white,
        },
      },
      drdPanel: {
        background: DARK_COLORS.ui02,
        boxShadow: rgba(SEMANTIC_COLORS.black, 0.5),
        buttonColor: SEMANTIC_COLORS.white,
      },
      dateRangePopover: {
        titleColor: SEMANTIC_COLORS.white,
        borderColor: LIGHT_COLORS.ui06,
        color: LIGHT_COLORS.ui04,
      },
      resourceDeletionModal: {
        detailsTable: {
          color: SEMANTIC_COLORS.white,
          backgroundColor: DARK_COLORS.ui03,
          border: DARK_COLORS.ui04,
        },
      },
      modules: {
        badge: {
          filters: {
            color: DARK_COLORS.ui02,
          },
          default: {
            backgroundColor: LIGHT_COLORS.ui05,
            color: DARK_COLORS.ui04,
          },
        },
        button: {
          secondary: {
            backgroundColor: DARK_COLORS.button03,
            borderColor: DARK_COLORS.ui05,
            color: LIGHT_COLORS.ui02,
            hover: {
              backgroundColor: DARK_COLORS.button04,
              borderColor: DARK_COLORS.button05,
              color: LIGHT_COLORS.ui02,
            },
            active: {
              borderColor: DARK_COLORS.button07,
              color: LIGHT_COLORS.ui02,
            },
            disabled: {
              backgroundColor: DARK_COLORS.button03,
              color: rgba(LIGHT_COLORS.ui02, 0.5),
            },
          },
          primary: {
            color: LIGHT_COLORS.ui04,
            disabled: {
              color: rgba(LIGHT_COLORS.ui04, 0.8),
            },
          },
          main: {
            color: LIGHT_COLORS.ui02,
            borderColor: DARK_COLORS.ui06,
            hover: {
              backgroundColor: DARK_COLORS.button01,
              borderColor: DARK_COLORS.button02,
            },
            focus: {
              borderColor: DARK_COLORS.ui06,
            },
            active: {
              backgroundColor: DARK_COLORS.ui04,
              borderColor: DARK_COLORS.ui05,
            },
            disabled: {
              color: rgba(LIGHT_COLORS.ui02, 0.5),
              backgroundColor: DARK_COLORS.button03,
              borderColor: DARK_COLORS.ui05,
            },
          },
        },
        checkbox: {
          label: {
            color: '#ececec',
          },
          customCheckbox: {
            before: {
              borderColor: '#bebec0',
              backgroundColor: DARK_COLORS.ui02,
              selection: {
                backgroundColor: SEMANTIC_COLORS.selections,
              },
            },
            after: {
              borderColor: SEMANTIC_COLORS.white,
              selection: {
                borderColor: SEMANTIC_COLORS.white,
              },
            },
          },
        },
        codeModal: {
          codeEditor: {
            borderColor: DARK_COLORS.ui02,
            backgroundColor: DARK_COLORS.ui01,
            pre: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
            },
          },
        },
        collapsablePanel: {
          collapsable: {
            backgroundColor: DARK_COLORS.ui03,
          },
          expandButton: {
            backgroundColor: DARK_COLORS.ui03,
          },
        },
        popover: {
          backgroundColor: DARK_COLORS.ui04,
          borderColor: DARK_COLORS.ui06,
          modificationsDropdown: {
            color: DARK_COLORS.linkDefault,
          },
          arrowStyle: {
            borderColor: DARK_COLORS.ui06,
          },
        },
        diagram: {
          modificationsBadgeOverlay: {
            backgroundColor: SEMANTIC_COLORS.primaryButton04,
          },
          statisticOverlay: {
            statistic: {
              active: {
                backgroundColor: SEMANTIC_COLORS.allIsWell,
                fadedBackgroundColor: DARK_COLORS.canceledBadgeActive,
              },
              incidents: {
                backgroundColor: SEMANTIC_COLORS.incidentsAndErrors,
                fadedBackgroundColor: DARK_COLORS.canceledBadgeIncident,
              },
              completed: {
                backgroundColor: LIGHT_COLORS.button02,
                fadedBackgroundColor: LIGHT_COLORS.button02,
              },
              canceled: {
                backgroundColor: SEMANTIC_COLORS.badge02,
                fadedBackgroundColor: SEMANTIC_COLORS.badge02,
              },
            },
          },
          outline: {
            fill: rgba(DARK_COLORS.selectedOdd, 0.5),
          },
          defaultFillColor: DARK_COLORS.ui02,
          defaultStrokeColor: SEMANTIC_COLORS.grey,
          element: {
            text: SEMANTIC_COLORS.white,
            background: {
              default: DARK_COLORS.ui02,
              selected: DARK_COLORS.selectedOdd,
            },
            border: SEMANTIC_COLORS.grey,
            outline: SEMANTIC_COLORS.selections,
          },
        },
        dropdown: {
          menu: {
            pointerBody: {
              borderColor: DARK_COLORS.ui04,
            },
            pointerShadow: {
              borderColor: DARK_COLORS.ui06,
            },
            ul: {
              borderColor: DARK_COLORS.ui06,
              backgroundColor: DARK_COLORS.ui04,
            },
            topPointer: {
              borderColor: DARK_COLORS.ui06,
            },
            bottomPointer: {
              borderColor: DARK_COLORS.ui06,
            },
            li: {
              borderColor: DARK_COLORS.ui06,
            },
          },
          option: {
            borderColor: DARK_COLORS.ui06,
            optionButton: {
              disabled: {
                color: rgba(SEMANTIC_COLORS.white, 0.6),
              },
              default: {
                color: rgba(SEMANTIC_COLORS.white, 0.9),
              },
              hover: {
                backgroundColor: DARK_COLORS.ui06,
              },
            },
          },

          button: {
            default: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
            },
            disabled: {
              color: rgba(SEMANTIC_COLORS.white, 0.6),
            },
          },
        },
        iconButton: {
          icon: {
            default: {
              svg: {
                color: SEMANTIC_COLORS.white,
              },
            },
            foldable: {
              svg: {
                color: LIGHT_COLORS.ui02,
              },
            },
          },
          button: {
            default: {
              hover: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
              active: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
            },
            foldable: {
              hover: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
              active: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
            },
          },
        },
        incidentOperation: {
          operationSpinner: {
            selected: {
              borderColor: SEMANTIC_COLORS.white,
            },
          },
        },
        input: {
          placeholder: {
            color: rgba(SEMANTIC_COLORS.white, 0.7),
          },
          backgroundColor: DARK_COLORS.ui02,
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          borderColor: DARK_COLORS.button02,
        },
        instancesBar: {
          greyTextStyle: {
            color: SEMANTIC_COLORS.white,
          },
          mediumTextStyle: {
            color: SEMANTIC_COLORS.white,
          },
        },
        messages: {
          warning: {
            backgroundColor: DARK_COLORS.warningNotification,
            color: SEMANTIC_COLORS.white,
          },
          error: {
            backgroundColor: DARK_COLORS.errorNotification,
            color: SEMANTIC_COLORS.white,
          },
        },
        modal: {
          modalRoot: {
            backgroundColor: rgba(SEMANTIC_COLORS.black, 0.5),
          },
          modalContent: {
            borderColor: DARK_COLORS.ui06,
          },
          modalHeader: {
            borderColor: DARK_COLORS.ui06,
          },
          crossButton: {
            active: {
              color: SEMANTIC_COLORS.white,
            },
          },
          modalBody: {
            backgroundColor: DARK_COLORS.ui01,
          },
          modalFooter: {
            backgroundColor: DARK_COLORS.ui02,
            borderColor: DARK_COLORS.ui06,
          },
          closeButton: {
            color: LIGHT_COLORS.ui02,
          },
        },
        operationItems: {
          iconStyle: {
            color: SEMANTIC_COLORS.white,
          },
          default: {
            background: DARK_COLORS.ui04,
            border: DARK_COLORS.ui06,
          },
          hover: {
            background: DARK_COLORS.button05,
            border: DARK_COLORS.button02,
          },
          active: {
            background: DARK_COLORS.button03,
            border: DARK_COLORS.ui05,
          },
        },
        operations: {
          default: {
            borderColor: SEMANTIC_COLORS.white,
          },
          selected: {
            borderColor: SEMANTIC_COLORS.white,
          },
        },
        panel: {
          panelFooter: {
            backgroundColor: DARK_COLORS.ui03,
          },
          panelHeader: {
            backgroundColor: DARK_COLORS.ui03,
          },
          backgroundColor: DARK_COLORS.ui02,
        },
        pill: {
          default: {
            color: SEMANTIC_COLORS.white,
            borderColor: DARK_COLORS.ui06,
            backgroundColor: DARK_COLORS.ui05,
          },
          active: {
            borderColor: SEMANTIC_COLORS.primaryButton03,
            backgroundColor: SEMANTIC_COLORS.selections,
          },
          disabled: {
            borderColor: DARK_COLORS.ui05,
            backgroundColor: DARK_COLORS.button03,
            color: rgba(SEMANTIC_COLORS.white, 0.5),
          },
          hover: {
            backgroundColor: DARK_COLORS.pillHover,
          },
          count: {
            default: {
              backgroundColor: DARK_COLORS.button02,
            },
            active: {
              backgroundColor: SEMANTIC_COLORS.white,
            },
            hover: {
              backgroundColor: DARK_COLORS.button02,
            },
          },
        },
        select: {
          default: {
            backgroundColor: '#3e3f45',
          },
          disabled: {
            backgroundColor: rgba('#3e3f45', 0.4),
            borderColor: rgba(DARK_COLORS.ui05, 0.2),
            color: rgba(SEMANTIC_COLORS.white, 0.5),
          },
        },
        skeleton: {
          backgroundColor: SEMANTIC_COLORS.badge02,
        },
        spinner: {
          borderColor: SEMANTIC_COLORS.white,
        },
        spinnerSkeleton: {
          skeleton: {
            backgroundColor: rgba(SEMANTIC_COLORS.black, 0.65),
          },
          skeletonSpinner: {
            borderColor: SEMANTIC_COLORS.white,
          },
        },
        stateIcon: {
          completedIcon: {
            color: LIGHT_COLORS.button02,
          },
        },
        table: {
          th: {
            color: rgba(SEMANTIC_COLORS.white, 0.8),
            after: {
              backgroundColor: DARK_COLORS.ui04,
            },
          },
          td: {
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
          columnHeader: {
            color: LIGHT_COLORS.ui02,
            disabled: {
              color: SEMANTIC_COLORS.white,
            },
            sortingActive: {
              color: SEMANTIC_COLORS.white,
            },
          },
        },
        textarea: {
          backgroundColor: DARK_COLORS.ui02,
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          placeholder: {
            color: rgba(SEMANTIC_COLORS.white, 0.7),
          },
        },
        tabView: {
          backgroundColor: DARK_COLORS.ui02,
          header: {
            backgroundColor: DARK_COLORS.ui03,
          },
        },
      },
    },
    opacity: {
      metricPanel: {
        skeletonBar: 0.2,
      },
      dashboard: {
        tileTitle: 0.9,
        skeleton: {
          block: 0.2,
        },
      },
      flowNodeInstancesTree: {
        bar: {
          nodeIcon: {
            default: 0.75,
            selected: 0.8,
          },
          nodeName: {
            default: 0.9,
            selected: 0.9,
          },
        },
      },
      columnHeader: {
        label: {
          default: 0.7,
          active: 0.9,
          disabled: 0.5,
        },
        sortIcon: {
          default: 0.6,
          active: 0.9,
          disabled: 0.3,
        },
      },
      progressBar: {
        background: 0.2,
      },
      operationsEntry: {
        iconStyle: 0.9,
      },
      modules: {
        badge: 0.8,
        checkbox: {
          default: 0.7,
          checked: 0.9,
        },
        codeModal: {
          codeEditor: 0.5,
        },
        collapseButton: {
          icons: {
            default: 0.5,
            hover: 0.7,
          },
        },
        copyright: 0.7,
        iconButton: {
          icon: {
            default: {
              svg: 1,
            },
            foldable: {
              svg: 1,
            },
          },
          button: {
            default: {
              hover: {
                before: 0.25,
                svg: 1,
              },
              active: {
                before: 0.4,
                svg: 1,
              },
            },
            foldable: {
              hover: {
                before: 0.25,
                svg: 1,
              },
              active: {
                before: 0.4,
                svg: 1,
              },
            },
          },
        },
        instancesBar: {
          label: 0.9,
          bar: {
            active: 0.9,
          },
        },
        modal: {
          crossButton: {
            default: 0.5,
            hover: 0.7,
          },
          modalBodyText: 0.9,
        },
        pill: {
          default: 1,
          active: 1,
          count: {
            default: 1,
            active: 1,
          },
        },
        skeleton: 0.2,
        stateIcon: {
          aliasIcon: 0.46,
        },
      },
    },
    shadows: {
      panelListItem: {
        hover: `0 0 4px 0 ${SEMANTIC_COLORS.black}`,
        active: `inset 0 0 6px 0 ${rgba(SEMANTIC_COLORS.black, 0.4)}`,
      },
      dashboard: {
        panelStyles: `0 3px 6px 0 ${SEMANTIC_COLORS.black}`,
      },
      filters: {
        resetButtonContainer: `0px -2px 4px 0px ${rgba(
          SEMANTIC_COLORS.black,
          0.1
        )}`,
      },
      variablesPanel: {
        footer: `0 -1px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        ioMappings: {
          banner: `1px 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        },
      },
      modificationMode: {
        footer: `0 -1px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        lastModification: `0 3px 4px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
      },
      topPanel: {
        modificationInfoBanner: `0 2px 4px 0 ${rgba(
          SEMANTIC_COLORS.black,
          0.2
        )}`,
      },
      modules: {
        button: {
          default: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.35)}`,
          primaryFocus: `0 0 0 1px ${DARK_COLORS.linkHover}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
        },
        checkbox: {
          customCheckbox: {
            before: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.5)}`,
            selection: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.5)}`,
          },
        },
        popover: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.6)}`,
        messages: {
          warning: `1px 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
          error: `1px 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        },
        dropdown: {
          menu: {
            ul: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.6)}`,
          },
        },
        operationItems: {
          ul: `0 1px 1px 0 ${rgba(SEMANTIC_COLORS.black, 0.3)}`,
        },
        select: {
          box: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.35)}`,
          text: `0 0 0 ${SEMANTIC_COLORS.white}`,
        },
        focus: `0 0 0 1px ${DARK_COLORS.focusInner}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
      },
    },
    images: {
      zeebraStripe: darkZeebraStripe,
      incidentsOverlay: incidentsOverlayDarkBackgroundImage,
    },
  },
  light: {
    cmTheme: 'Light',
    colors: {
      ...LIGHT_COLORS,
      text01: DARK_COLORS.ui04,
      text02: DARK_COLORS.ui06,
      borderColor: LIGHT_COLORS.ui05,
      layout: {
        dashboard: {
          backgroundColor: LIGHT_COLORS.ui01,
        },
        default: {
          backgroundColor: LIGHT_COLORS.ui02,
        },
      },
      decisionInstance: {
        header: {
          backgroundColor: LIGHT_COLORS.ui02,
        },
        backgroundColor: LIGHT_COLORS.ui04,
      },
      metricPanel: {
        skeletonBar: {
          backgroundColor: LIGHT_COLORS.ui06,
        },
        color: LIGHT_COLORS.ui06,
      },
      panelListItem: {
        active: {
          borderColor: rgba(LIGHT_COLORS.ui05, 0.4),
        },
        hover: {
          borderColor: rgba(LIGHT_COLORS.ui05, 0.5),
        },
      },
      dashboard: {
        backgroundColor: LIGHT_COLORS.ui02,
        panelStyles: {
          backgroundColor: LIGHT_COLORS.ui04,
        },
        metricPanelWrapper: {
          color: LIGHT_COLORS.ui06,
        },
        skeleton: {
          block: {
            backgroundColor: LIGHT_COLORS.ui06,
          },
        },
        message: {
          default: {
            color: rgba(LIGHT_COLORS.ui06, 0.8),
          },
          error: {
            color: rgba(SEMANTIC_COLORS.incidentsAndErrors, 0.9),
          },
          success: {
            color: rgba(SEMANTIC_COLORS.allIsWell, 0.9),
          },
        },
      },
      header: {
        separator: LIGHT_COLORS.ui05,
        user: {
          backgroundColor: rgba(LIGHT_COLORS.ui06, 0.09),
        },
        details: {
          borderColor: rgba(LIGHT_COLORS.ui06, 0.25),
        },
        skeleton: {
          backgroundColor: rgba(LIGHT_COLORS.ui06, 0.09),
        },
        license: {
          backgroundColor: LIGHT_COLORS.ui02,
          borderColor: LIGHT_COLORS.ui05,
          arrowStyle: {
            before: {
              borderColor: LIGHT_COLORS.ui05,
            },
            after: {
              borderColor: LIGHT_COLORS.ui02,
            },
          },
        },
      },
      variablesPanel: {
        footer: {
          backgroundColor: LIGHT_COLORS.button05,
        },
        ioMappings: {
          banner: {
            backgroundColor: LIGHT_COLORS.focusInner,
            closeIconColor: DARK_COLORS.ui04,
          },
        },
      },
      variables: {
        skeleton: {
          backgroundColor: LIGHT_COLORS.ui04,
        },
        placeholder: {
          color: LIGHT_COLORS.ui06,
        },
        tHead: {
          backgroundColor: LIGHT_COLORS.ui04,
        },
        editButton: {
          disabled: {
            color: DARK_COLORS.ui05,
          },
        },
        icons: {
          color: DARK_COLORS.ui04,
        },
        variablesTable: {
          tr: {
            backgroundColor: LIGHT_COLORS.button05,
          },
        },
        backdrop: {
          backgroundColor: rgba(SEMANTIC_COLORS.white, 0.75),
          spinner: {
            borderColor: LIGHT_COLORS.ui06,
          },
        },
        color: rgba(LIGHT_COLORS.ui06, 0.8),
      },
      flowNodeInstanceLog: {
        color: rgba(LIGHT_COLORS.ui06, 0.9),
      },
      flowNodeInstancesTree: {
        bar: {
          nodeName: {
            selected: {
              borderColor: rgba(LIGHT_COLORS.ui06, 0.25),
            },
          },
          placeholder: {
            modificationIcon: {
              color: SEMANTIC_COLORS.primaryButton04,
            },
          },
        },
        foldable: {
          summaryLabel: {
            backgroundColor: LIGHT_COLORS.ui05,
          },
        },
        timeStampLabel: {
          backgroundColor: rgba(LIGHT_COLORS.ui04, 0.55),
        },
        connectionDot: {
          color: LIGHT_COLORS.ui05,
        },
        nodeDetails: {
          color: rgba(DARK_COLORS.ui04, 0.9),
          selected: {
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
        },
        ul: {
          backgroundColor: LIGHT_COLORS.ui05,
        },
      },
      incidentsFilter: {
        filtersWrapper: {
          backgroundColor: LIGHT_COLORS.ui02,
        },
        label: {
          backgroundColor: LIGHT_COLORS.ui05,
        },
        moreDropdown: {
          dropdownToggle: {
            borderColor: LIGHT_COLORS.ui03,
            color: DARK_COLORS.ui04,
          },
          item: {
            borderColor: DARK_COLORS.ui04,
          },
        },
        buttonsWrapper: {
          backgroundColor: LIGHT_COLORS.ui05,
        },
      },
      incidentsOverlay: {
        backgroundColor: LIGHT_COLORS.ui04,
      },
      instanceHeader: {
        backgroundColor: LIGHT_COLORS.ui04,
      },
      topPanel: {
        backgroundColor: LIGHT_COLORS.ui04,
        instanceHeader: {
          backgroundColor: LIGHT_COLORS.ui02,
        },
        modificationInfoBanner: {
          backgroundColor: SEMANTIC_COLORS.supportWarning,
          separatorColor: SEMANTIC_COLORS.borderStrong01,
          color: DARK_COLORS.ui04,
          linkButton: {
            default: LIGHT_COLORS.linkDefault,
            hover: LIGHT_COLORS.linkHover,
            active: LIGHT_COLORS.linkActive,
          },
        },
      },
      emptyMessage: {
        color: LIGHT_COLORS.ui06,
      },
      emptyState: {
        color: LIGHT_COLORS.ui06,
      },
      decisionsList: {
        backgroundColor: LIGHT_COLORS.ui04,
        header: {
          backgroundColor: LIGHT_COLORS.ui02,
        },
      },
      list: {
        backgroundColor: LIGHT_COLORS.ui04,
      },
      createOperationDropdown: {
        dropdownButtonStyles: {
          color: LIGHT_COLORS.ui04,
        },
      },
      operationsEntry: {
        entry: {
          color: rgba(LIGHT_COLORS.ui06, 0.9),
          isRunning: {
            backgroundColor: LIGHT_COLORS.ui04,
          },
        },
        iconStyle: {
          color: DARK_COLORS.ui02,
        },
      },
      operationsPanel: {
        skeleton: {
          entry: {
            backgroundColor: LIGHT_COLORS.ui04,
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
        },
        emptyMessage: {
          color: LIGHT_COLORS.ui06,
          backgroundColor: LIGHT_COLORS.ui04,
        },
      },
      operationsProgressBar: {
        shimmerColor: SEMANTIC_COLORS.shimmer,
      },
      processInstance: {
        modifications: {
          footer: {
            backgroundColor: LIGHT_COLORS.ui02,
            lastModification: {
              backgroundColor: LIGHT_COLORS.ui05,
              buttonColor: LIGHT_COLORS.linkDefault,
              separatorColor: LIGHT_COLORS.ui03,
              color: DARK_COLORS.ui04,
            },
          },
          helperModal: {
            modificationType: {
              color: LIGHT_COLORS.linkDefault,
            },
          },
          summaryModal: {
            tableHeader: {
              backgroundColor: LIGHT_COLORS.ui04,
            },
          },
          loadingOverlay: {
            backgroundColor: rgba(SEMANTIC_COLORS.white, 0.75),
            spinner: {
              borderColor: LIGHT_COLORS.ui06,
            },
          },
        },
      },
      sortableTable: {
        backgroundColor: LIGHT_COLORS.ui04,
        hover: LIGHT_COLORS.rowHover,
        header: {
          tr: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
        },
        tr: {
          selected: {
            backgroundColor: LIGHT_COLORS.focusInner,
          },
        },
      },
      dataTable: {
        backgroundColor: LIGHT_COLORS.ui04,
        header: {
          tr: {
            backgroundColor: LIGHT_COLORS.ui04,
          },
        },
      },
      filtersPanel: {
        modalIcon: {
          color: DARK_COLORS.ui04,
        },
      },
      disclaimer: {
        container: {
          color: '#7e7e7f',
        },
      },
      login: {
        input: {
          backgroundColor: LIGHT_COLORS.ui04,
          borderColor: LIGHT_COLORS.ui03,
          labelColor: LIGHT_COLORS.ui06,
          focusInner: DARK_COLORS.focusInner,
        },
      },
      decisionViewer: {
        background: LIGHT_COLORS.ui04,
        text: DARK_COLORS.ui01,
        border: LIGHT_COLORS.ui06,
        highlightedRow: {
          background: '#dde8fd',
          color: DARK_COLORS.ui01,
        },
      },
      drdPanel: {
        background: LIGHT_COLORS.ui04,
        boxShadow: rgba(SEMANTIC_COLORS.black, 0.3),
        buttonColor: DARK_COLORS.ui04,
      },
      dateRangePopover: {
        titleColor: DARK_COLORS.ui04,
        borderColor: LIGHT_COLORS.ui05,
        color: LIGHT_COLORS.ui06,
      },
      resourceDeletionModal: {
        detailsTable: {
          color: DARK_COLORS.ui04,
          backgroundColor: LIGHT_COLORS.ui02,
          border: LIGHT_COLORS.ui05,
        },
      },
      modules: {
        badge: {
          filters: {
            color: DARK_COLORS.ui02,
          },
          default: {
            backgroundColor: DARK_COLORS.ui04,
            color: SEMANTIC_COLORS.white,
          },
        },
        button: {
          secondary: {
            backgroundColor: LIGHT_COLORS.button04,
            borderColor: LIGHT_COLORS.ui03,
            color: rgba(DARK_COLORS.ui04, 0.9),
            hover: {
              backgroundColor: LIGHT_COLORS.button05,
              borderColor: LIGHT_COLORS.ui03,
              color: rgba(DARK_COLORS.ui04, 0.9),
            },
            active: {
              borderColor: LIGHT_COLORS.ui03,
              color: rgba(DARK_COLORS.ui02, 0.9),
            },
            disabled: {
              backgroundColor: LIGHT_COLORS.button04,
              color: rgba(DARK_COLORS.ui04, 0.5),
            },
          },
          primary: {
            color: LIGHT_COLORS.ui04,
            disabled: {
              color: rgba(LIGHT_COLORS.ui04, 0.8),
            },
          },
          main: {
            color: rgba(DARK_COLORS.ui04, 0.9),
            borderColor: LIGHT_COLORS.ui03,
            hover: {
              backgroundColor: LIGHT_COLORS.button01,
              borderColor: LIGHT_COLORS.button02,
            },
            focus: {
              borderColor: LIGHT_COLORS.ui03,
            },
            active: {
              backgroundColor: LIGHT_COLORS.ui03,
              borderColor: LIGHT_COLORS.button03,
            },
            disabled: {
              color: rgba(DARK_COLORS.ui04, 0.5),
              backgroundColor: LIGHT_COLORS.button04,
              borderColor: LIGHT_COLORS.ui03,
            },
          },
        },
        checkbox: {
          label: {
            color: LIGHT_COLORS.ui06,
          },
          customCheckbox: {
            before: {
              borderColor: LIGHT_COLORS.ui03,
              backgroundColor: LIGHT_COLORS.ui01,
              selection: {
                backgroundColor: SEMANTIC_COLORS.selections,
              },
            },
            after: {
              borderColor: LIGHT_COLORS.ui06,
              selection: {
                borderColor: SEMANTIC_COLORS.white,
              },
            },
          },
        },
        codeModal: {
          codeEditor: {
            borderColor: LIGHT_COLORS.ui05,
            backgroundColor: LIGHT_COLORS.ui04,
            pre: {
              color: LIGHT_COLORS.ui06,
            },
          },
        },
        collapsablePanel: {
          collapsable: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
          expandButton: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
        },
        popover: {
          backgroundColor: LIGHT_COLORS.ui02,
          borderColor: LIGHT_COLORS.ui05,
          modificationsDropdown: {
            color: LIGHT_COLORS.linkDefault,
          },
          arrowStyle: {
            borderColor: LIGHT_COLORS.ui05,
          },
        },
        diagram: {
          modificationsBadgeOverlay: {
            backgroundColor: SEMANTIC_COLORS.primaryButton04,
          },
          statisticOverlay: {
            statistic: {
              active: {
                backgroundColor: SEMANTIC_COLORS.allIsWell,
                fadedBackgroundColor: LIGHT_COLORS.canceledBadgeActive,
              },
              incidents: {
                backgroundColor: SEMANTIC_COLORS.incidentsAndErrors,
                fadedBackgroundColor: LIGHT_COLORS.canceledBadgeIncident,
              },
              completed: {
                backgroundColor: LIGHT_COLORS.button02,
                fadedBackgroundColor: LIGHT_COLORS.button02,
              },
              canceled: {
                backgroundColor: SEMANTIC_COLORS.badge02,
                fadedBackgroundColor: SEMANTIC_COLORS.badge02,
              },
            },
          },
          outline: {
            fill: rgba(LIGHT_COLORS.selectedEven, 0.5),
          },
          defaultFillColor: LIGHT_COLORS.ui04,
          defaultStrokeColor: LIGHT_COLORS.ui06,
          element: {
            text: DARK_COLORS.ui01,
            background: {
              default: LIGHT_COLORS.ui04,
              selected: '#dde8fd',
            },
            border: LIGHT_COLORS.ui06,
            outline: SEMANTIC_COLORS.selections,
          },
        },
        dropdown: {
          menu: {
            pointerBody: {
              borderColor: LIGHT_COLORS.ui02,
            },
            pointerShadow: {
              borderColor: LIGHT_COLORS.ui05,
            },
            ul: {
              borderColor: LIGHT_COLORS.ui05,
              backgroundColor: LIGHT_COLORS.ui02,
            },
            topPointer: {
              borderColor: LIGHT_COLORS.ui05,
            },
            bottomPointer: {
              borderColor: LIGHT_COLORS.ui05,
            },
            li: {
              borderColor: LIGHT_COLORS.ui05,
            },
          },
          option: {
            borderColor: LIGHT_COLORS.ui05,
            optionButton: {
              disabled: {
                color: rgba(LIGHT_COLORS.ui06, 0.6),
              },
              default: {
                color: rgba(LIGHT_COLORS.ui06, 0.9),
              },
              hover: {
                backgroundColor: LIGHT_COLORS.ui05,
              },
            },
          },
          button: {
            default: {
              color: rgba(LIGHT_COLORS.ui06, 0.9),
            },
            disabled: {
              color: rgba(LIGHT_COLORS.ui06, 0.6),
            },
          },
        },
        iconButton: {
          icon: {
            default: {
              svg: {
                color: DARK_COLORS.ui04,
              },
            },
            foldable: {
              svg: {
                color: DARK_COLORS.ui04,
              },
            },
          },
          button: {
            default: {
              hover: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
              active: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
            },
            foldable: {
              hover: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
              active: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
            },
          },
        },
        incidentOperation: {
          operationSpinner: {
            selected: {
              borderColor: SEMANTIC_COLORS.selections,
            },
          },
        },
        input: {
          placeholder: {
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
          backgroundColor: LIGHT_COLORS.ui04,
          color: rgba(DARK_COLORS.ui03, 0.9),
          borderColor: LIGHT_COLORS.ui03,
        },
        instancesBar: {
          greyTextStyle: {
            color: SEMANTIC_COLORS.badge02,
          },
          mediumTextStyle: {
            color: LIGHT_COLORS.ui06,
          },
        },
        messages: {
          warning: {
            backgroundColor: LIGHT_COLORS.warningNotification,
            color: DARK_COLORS.ui04,
          },
          error: {
            backgroundColor: LIGHT_COLORS.errorNotification,
            color: DARK_COLORS.ui04,
          },
        },
        modal: {
          modalRoot: {
            backgroundColor: rgba(SEMANTIC_COLORS.white, 0.7),
          },
          modalContent: {
            borderColor: LIGHT_COLORS.ui05,
          },
          modalHeader: {
            borderColor: LIGHT_COLORS.ui05,
          },
          crossButton: {
            active: {
              color: DARK_COLORS.ui04,
            },
          },
          modalBody: {
            backgroundColor: LIGHT_COLORS.ui04,
          },
          modalFooter: {
            backgroundColor: LIGHT_COLORS.ui04,
            borderColor: LIGHT_COLORS.ui05,
          },
          closeButton: {
            color: LIGHT_COLORS.ui02,
          },
        },
        operationItems: {
          iconStyle: {
            color: DARK_COLORS.ui02,
          },
          default: {
            background: LIGHT_COLORS.button04,
            border: LIGHT_COLORS.ui03,
          },
          hover: {
            background: LIGHT_COLORS.button06,
            border: LIGHT_COLORS.button02,
          },
          active: {
            background: LIGHT_COLORS.ui03,
            border: LIGHT_COLORS.button03,
          },
        },
        operations: {
          default: {
            borderColor: LIGHT_COLORS.ui06,
          },
          selected: {
            borderColor: SEMANTIC_COLORS.selections,
          },
        },
        panel: {
          panelFooter: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
          panelHeader: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
          backgroundColor: LIGHT_COLORS.ui04,
        },
        pill: {
          default: {
            color: DARK_COLORS.ui05,
            borderColor: LIGHT_COLORS.ui03,
            backgroundColor: LIGHT_COLORS.ui05,
          },
          active: {
            borderColor: SEMANTIC_COLORS.primaryButton03,
            backgroundColor: SEMANTIC_COLORS.selections,
          },
          disabled: {
            borderColor: LIGHT_COLORS.ui03,
            backgroundColor: LIGHT_COLORS.button04,
            color: rgba(DARK_COLORS.ui04, 0.5),
          },
          hover: {
            backgroundColor: LIGHT_COLORS.button01,
          },
          count: {
            default: {
              backgroundColor: LIGHT_COLORS.ui06,
            },
            active: {
              backgroundColor: SEMANTIC_COLORS.white,
            },
            hover: {
              backgroundColor: LIGHT_COLORS.ui06,
            },
          },
        },
        select: {
          default: {
            backgroundColor: LIGHT_COLORS.ui01,
          },
          disabled: {
            backgroundColor: rgba(LIGHT_COLORS.ui01, 0.4),
            borderColor: rgba(LIGHT_COLORS.ui03, 0.2),
            color: rgba(LIGHT_COLORS.ui06, 0.7),
          },
        },
        skeleton: {
          backgroundColor: LIGHT_COLORS.ui06,
        },
        spinner: {
          borderColor: SEMANTIC_COLORS.badge02,
        },
        spinnerSkeleton: {
          skeleton: {
            backgroundColor: rgba(SEMANTIC_COLORS.white, 0.75),
          },
          skeletonSpinner: {
            borderColor: LIGHT_COLORS.ui06,
          },
        },
        stateIcon: {
          completedIcon: {
            color: LIGHT_COLORS.button02,
          },
        },
        table: {
          th: {
            color: rgba(LIGHT_COLORS.ui06, 0.8),
            after: {
              backgroundColor: LIGHT_COLORS.ui05,
            },
          },
          td: {
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
          columnHeader: {
            color: LIGHT_COLORS.ui06,
            disabled: {
              color: LIGHT_COLORS.ui06,
            },
            sortingActive: {
              color: DARK_COLORS.ui04,
            },
          },
        },
        textarea: {
          backgroundColor: LIGHT_COLORS.ui04,
          color: DARK_COLORS.ui03,
          placeholder: {
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
        },
        tabView: {
          backgroundColor: LIGHT_COLORS.ui04,
          header: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
        },
      },
    },
    opacity: {
      metricPanel: {
        skeletonBar: 0.09,
      },
      dashboard: {
        tileTitle: 1,
        skeleton: {
          block: 0.09,
        },
      },
      flowNodeInstancesTree: {
        bar: {
          nodeIcon: {
            default: 0.6,
            selected: 0.65,
          },
          nodeName: {
            default: 0.9,
            selected: 1,
          },
        },
      },
      columnHeader: {
        label: {
          default: 0.8,
          active: 1,
          disabled: 0.6,
        },
        sortIcon: {
          default: 0.6,
          active: 1,
          disabled: 0.3,
        },
      },
      progressBar: {
        background: 0.3,
      },
      operationsEntry: {
        iconStyle: 0.8,
      },
      modules: {
        badge: 0.7,
        checkbox: {
          default: 0.7,
          checked: 1,
        },
        codeModal: {
          codeEditor: 0.65,
        },
        collapseButton: {
          icons: {
            default: 0.9,
            hover: 1,
          },
        },
        copyright: 0.9,
        iconButton: {
          icon: {
            default: {
              svg: 0.9,
            },
            foldable: {
              svg: 0.9,
            },
          },
          button: {
            default: {
              hover: {
                svg: 0.9,
                before: 0.5,
              },
              active: {
                svg: 1,
                before: 0.8,
              },
            },
            foldable: {
              hover: {
                before: 0.5,
                svg: 0.9,
              },
              active: {
                before: 0.8,
                svg: 1,
              },
            },
          },
        },
        instancesBar: {
          label: 1,
          bar: {
            active: 0.4,
          },
        },
        modal: {
          crossButton: {
            default: 0.9,
            hover: 1,
          },
          modalBodyText: 1,
        },
        pill: {
          default: 0.5,
          active: 1,
          count: {
            default: 0.55,
            active: 1,
          },
        },
        skeleton: 0.09,
        stateIcon: {
          aliasIcon: 0.4,
        },
      },
    },
    shadows: {
      panelListItem: {
        hover: `0 0 5px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        active: `inset 0 0 6px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
      },
      dashboard: {
        panelStyles: `0 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
      },
      filters: {
        resetButtonContainer: `0px -1px 2px 0px ${rgba(
          SEMANTIC_COLORS.black,
          0.1
        )}`,
      },
      variablesPanel: {
        footer: `0 -1px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        ioMappings: {
          banner: `1px 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        },
      },
      modificationMode: {
        footer: `0 -1px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        lastModification: `0 3px 4px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
      },
      topPanel: {
        modificationInfoBanner: `0 2px 4px 0 ${rgba(
          SEMANTIC_COLORS.black,
          0.2
        )}`,
      },
      modules: {
        button: {
          default: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.08)}`,
          primaryFocus: `0 0 0 1px ${DARK_COLORS.linkHover}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
        },
        checkbox: {
          customCheckbox: {
            before: `0 2px 2px 0 ${rgba(LIGHT_COLORS.button05, 0.35)}`,
            selection: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.5)}`,
          },
        },
        popover: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
        dropdown: {
          menu: {
            ul: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
          },
        },
        messages: {
          warning: `1px 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
          error: `1px 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        },
        operationItems: {
          ul: `0 1px 1px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        },
        select: {
          box: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.08)}`,
          text: `0 0 0 ${DARK_COLORS.ui06}`,
        },
        focus: `0 0 0 1px ${DARK_COLORS.focusInner}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
      },
    },
    images: {
      zeebraStripe: lightZeebraStripe,
      incidentsOverlay: incidentsOverlayLightBackgroundImage,
    },
  },
} as const;

export {theme};
