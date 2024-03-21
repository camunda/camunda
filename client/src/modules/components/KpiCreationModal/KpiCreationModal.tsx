/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {ComboBox, Stack} from '@carbon/react';
import {useHistory, useLocation} from 'react-router-dom';
import update from 'immutability-helper';

import {DefinitionSelection, TargetSelection, ReportRenderer} from 'components';
import {t} from 'translation';
import {getCollection, createEntity, evaluateReport, ReportPayload} from 'services';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {newReport} from 'config';
import {SingleProcessReport} from 'types';

import {KpiTemplate} from './templates/types';
import {automationRate} from './templates';
import {MultiStepModal} from './MultiStepModal';

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
  const [evaluatedReport, setEvaluatedReport] = useState<SingleProcessReport>();
  const history = useHistory();
  const {pathname} = useLocation();
  const kpiTemplates = [automationRate()];
  const {mightFail} = useErrorHandling();

  const loadReport = (reportPayload: ReportPayload<'process'>) =>
    mightFail(evaluateReport(reportPayload, []), setEvaluatedReport, showError);

  return (
    <MultiStepModal
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
                  if (selectedItem) {
                    setSelectedKpi(selectedItem);
                  }
                }}
                id="KpiSelectionComboBox"
                items={kpiTemplates}
                itemToString={(item) => (item ? item.name : '')}
                titleText={t('report.kpiTemplates.selectKpi')}
              />
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

                const newReportPayload = newReport.new as unknown as ReportPayload<'process'>;
                await loadReport({
                  ...newReportPayload,
                  collectionId: getCollection(pathname),
                  name: selectedKpi.name,
                  description: selectedKpi.description,
                  data: {
                    ...selectedKpi.config,
                    configuration: {
                      ...newReportPayload.data?.configuration,
                      targetValue: {
                        ...newReportPayload.data?.configuration?.targetValue,
                        isKpi: true,
                        active: true,
                      },
                    },
                    definitions: [
                      {
                        identifier: 'definition',
                        ...definition,
                      },
                    ],
                  },
                });
              },
            },
          },
        },
        {
          title: t('report.kpiTemplates.step', {count: 2}).toString(),
          subtitle: t('report.config.goal.legend').toString(),
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
                  />

                  <ReportRenderer report={evaluatedReport} loadReport={loadReport} />
                </>
              )}
            </Stack>
          ),
          actions: {
            primary: {
              label: t('report.kpiTemplates.create'),
              kind: 'primary',
              disabled: !definition.key || !selectedKpi,
              onClick: async () => {
                await mightFail(
                  createEntity('report/process/single', evaluatedReport),
                  (id) => history.push(`report/${id}/`),
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
