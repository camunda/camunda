/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {ComboBox, Stack, Tile, Button, Layer, FormLabel, TextAreaSkeleton} from '@carbon/react';
import {CheckmarkFilled, CircleDash, Close, Edit} from '@carbon/icons-react';
import {useHistory, useLocation} from 'react-router-dom';
import update from 'immutability-helper';

import {DefinitionSelection, TargetSelection, ReportRenderer} from 'components';
import {t} from 'translation';
import {getCollection, createEntity, evaluateReport, ReportEvaluationPayload} from 'services';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {newReport} from 'config';
import {ProcessFilter, Report, FilterType} from 'types';
import {DateFilter, NodeSelection, FilterProps} from 'filter';
import {track} from 'tracking';

import {KpiTemplate, DefaultProcessFilter} from './templates/types';
import {automationRate, throughput} from './templates';
import {MultiStepModal} from './MultiStepModal';

import './KpiCreationModal.scss';

const DEFINITION_IDENTIFIER = 'definition';

interface KpiCreationModalProps {
  onClose: () => void;
}

export default function KpiCreationModal({onClose}: KpiCreationModalProps): JSX.Element {
  const [selectedKpi, setSelectedKpi] = useState<KpiTemplate | null>(null);
  const [definition, setDefinition] = useState<{
    key: string;
    versions: string[];
    tenantIds: string[];
  }>({
    key: '',
    versions: [],
    tenantIds: [],
  });
  const [evaluatedReport, setEvaluatedReport] = useState<Report>();
  const [loading, setLoading] = useState(false);
  const [openedFilter, setOpenedFilter] = useState<DefaultProcessFilter | null>(null);
  const history = useHistory();
  const {pathname} = useLocation();
  const kpiTemplates = [automationRate(), throughput()];
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    track('openKpiWizzard');
  }, []);

  const loadReport = async (reportPayload: ReportEvaluationPayload) => {
    setLoading(true);
    await mightFail(evaluateReport(reportPayload), setEvaluatedReport, showError, () =>
      setLoading(false)
    );
  };

  const getFilterModal = (
    type?: DefaultProcessFilter['type']
  ): React.FC<FilterProps<FilterType>> => {
    switch (type) {
      case 'instanceStartDate':
      case 'instanceEndDate':
        // @ts-expect-error There is some misalignment between the types
        return DateFilter;
      case 'executedFlowNodes':
        // @ts-expect-error There is some misalignment between the types
        return NodeSelection;
      default:
        return () => null;
    }
  };

  function getFilter(filter = openedFilter): ProcessFilter<FilterType> | undefined {
    if (!evaluatedReport || !filter) {
      return undefined;
    }

    const {type, filterLevel} = filter;
    return evaluatedReport.data.filter.find(
      (filter) => filter.type === type && filter.filterLevel === filterLevel
    );
  }

  const FilterModal = getFilterModal(openedFilter?.type);
  const allFiltersDefined =
    evaluatedReport?.data.filter.length === selectedKpi?.uiConfig.filters.length;

  return (
    <MultiStepModal
      className="KpiCreationModal"
      title={t('report.kpiTemplates.create').toString()}
      onClose={onClose}
      steps={[
        {
          title: t('report.kpiTemplates.step', {count: 1}).toString(),
          subtitle: t('report.kpiTemplates.selectKpi').toString(),
          content: (
            <Stack gap={6}>
              <ComboBox
                selectedItem={selectedKpi}
                onChange={({selectedItem}) => {
                  if (typeof selectedItem !== 'undefined') {
                    setSelectedKpi(selectedItem);
                  }
                }}
                id="KpiSelectionComboBox"
                items={kpiTemplates}
                itemToString={(item) => (item ? item.name : '')}
                titleText={t('report.kpiTemplates.selectKpi')}
              />
              {selectedKpi?.img && (
                <div className="preview">
                  <img src={selectedKpi.img} alt={selectedKpi.name} />
                </div>
              )}
              <DefinitionSelection
                definitionKey={definition.key}
                versions={definition.versions}
                tenants={definition.tenantIds}
                type="process"
                expanded
                onChange={({
                  key,
                  versions,
                  tenantIds,
                }: {
                  key: string;
                  name: string;
                  versions: string[];
                  tenantIds: string[];
                }) => {
                  setDefinition({
                    key,
                    versions,
                    tenantIds,
                  });
                }}
              />
            </Stack>
          ),
          actions: {
            primary: {
              label: t('common.nextStep'),
              kind: 'primary',
              disabled: !definition.key || !selectedKpi,
              onClick: async () => {
                if (!selectedKpi) {
                  return;
                }

                const newReportPayload = newReport.new as ReportEvaluationPayload;
                await loadReport({
                  ...newReportPayload,
                  collectionId: getCollection(pathname),
                  name: selectedKpi.name,
                  description: selectedKpi.description,
                  data: {
                    ...selectedKpi.config,
                    configuration: {
                      ...newReportPayload.data?.configuration,
                      ...selectedKpi.config.configuration,
                      targetValue: {
                        ...newReportPayload.data?.configuration?.targetValue,
                        isKpi: true,
                        active: true,
                      },
                    },
                    definitions: [
                      {
                        identifier: DEFINITION_IDENTIFIER,
                        ...definition,
                      },
                    ],
                    filter: newReportPayload.data?.filter,
                  },
                });
              },
            },
          },
        },
        {
          title: t('report.kpiTemplates.step', {count: 2}).toString(),
          subtitle: t('report.kpiTemplates.setTargetAndFilters').toString(),
          content: (
            <Stack gap={6}>
              {evaluatedReport && (
                <>
                  <TargetSelection
                    report={evaluatedReport}
                    onChange={(change) => {
                      setEvaluatedReport((evaluatedReport) =>
                        update(evaluatedReport, {
                          data: {configuration: change},
                        })
                      );
                    }}
                    hideBaseLine
                  />
                  <Layer>
                    <FormLabel>{t('report.kpiTemplates.filtersLabel')}</FormLabel>
                    <Stack gap={4}>
                      {selectedKpi?.uiConfig.filters.map((filter, idx) => {
                        const existingFilter = getFilter(filter);
                        return (
                          <Tile className="filterTile" key={`kpi-filter-${idx}`}>
                            {existingFilter ? (
                              <CheckmarkFilled size="20" />
                            ) : (
                              <CircleDash size="20" color="var(--cds-text-error)" />
                            )}
                            <span>{filter.label}</span>
                            <div className="actions">
                              {existingFilter ? (
                                <>
                                  <Button
                                    size="sm"
                                    kind="ghost"
                                    onClick={() =>
                                      setOpenedFilter({...filter, data: existingFilter?.data})
                                    }
                                    hasIconOnly
                                    renderIcon={Edit}
                                    iconDescription={t('common.edit').toString()}
                                  />
                                  <Button
                                    size="sm"
                                    kind="ghost"
                                    onClick={() => {
                                      setEvaluatedReport((evaluatedReport) => {
                                        if (!evaluatedReport) {
                                          return;
                                        }

                                        const filterIndex =
                                          evaluatedReport.data.filter.indexOf(existingFilter);
                                        const newReport = update(evaluatedReport, {
                                          data: {filter: {$splice: [[filterIndex, 1]]}},
                                        });

                                        loadReport(newReport!);
                                        return newReport;
                                      });
                                    }}
                                    hasIconOnly
                                    renderIcon={Close}
                                    iconDescription={t('common.delete').toString()}
                                  />
                                </>
                              ) : (
                                <Button
                                  size="sm"
                                  kind="tertiary"
                                  onClick={() => setOpenedFilter(filter)}
                                >
                                  {t('common.select')}
                                </Button>
                              )}
                            </div>
                          </Tile>
                        );
                      })}
                    </Stack>
                    {!allFiltersDefined && (
                      <FormLabel className="text-mandatory">
                        {t('report.kpiTemplates.mandatoryFields')}
                      </FormLabel>
                    )}
                  </Layer>
                  {loading ? (
                    <TextAreaSkeleton />
                  ) : (
                    <div>
                      <FormLabel>{t('report.kpiTemplates.preview')}</FormLabel>
                      <ReportRenderer report={evaluatedReport} />
                    </div>
                  )}
                  {openedFilter && (
                    <FilterModal
                      modalTitle={openedFilter.description}
                      className={openedFilter.type + 'KpiModal'}
                      definitions={evaluatedReport?.data.definitions}
                      filterData={{...openedFilter, appliedTo: [DEFINITION_IDENTIFIER]}}
                      addFilter={(newFilter) => {
                        if (!newFilter) {
                          return;
                        }

                        setEvaluatedReport((evaluatedReport) => {
                          if (!evaluatedReport) {
                            return;
                          }

                          const reportWithFilters = getReportWithFilters(
                            {...newFilter, filterLevel: openedFilter.filterLevel},
                            evaluatedReport
                          );
                          loadReport(reportWithFilters!);
                          return reportWithFilters;
                        });
                        setOpenedFilter(null);
                      }}
                      close={() => setOpenedFilter(null)}
                      filterType={openedFilter.type}
                      filterLevel={openedFilter.filterLevel}
                    />
                  )}
                </>
              )}
            </Stack>
          ),
          actions: {
            primary: {
              label: t('report.kpiTemplates.create'),
              kind: 'primary',
              disabled: !definition.key || !selectedKpi || !allFiltersDefined,
              onClick: async () => {
                await mightFail(
                  createEntity('report/process/single', evaluatedReport),
                  (id) => {
                    track('createKPIReport', {template: selectedKpi?.name});
                    history.push(`report/${id}/`);
                  },
                  showError
                );
              },
            },
            secondary: {
              label: t('common.previousStep'),
              kind: 'secondary',
            },
          },
        },
      ]}
    />
  );
}

function getReportWithFilters(newFilter: ProcessFilter<FilterType>, report: Report) {
  const existingFilter = report.data.filter.find(
    (filter) => filter.type === newFilter.type && filter.filterLevel === newFilter.filterLevel
  );
  if (existingFilter) {
    const index = report.data.filter.indexOf(existingFilter);
    return update(report, {
      data: {filter: {[index]: {$set: newFilter}}},
    });
  } else {
    return update(report, {
      data: {filter: {$push: [newFilter]}},
    });
  }
}
