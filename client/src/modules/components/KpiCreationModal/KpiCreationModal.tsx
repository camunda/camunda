/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {ComboBox, Stack} from '@carbon/react';
import {useHistory, useLocation} from 'react-router-dom';

import {DefinitionSelection} from 'components';
import {t} from 'translation';
import {getCollection, createEntity} from 'services';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {newReport} from 'config';

import {KpiTemplate} from './templates/types';
import {automationRate} from './templates';
import {MultiStepModal} from './MultiStepModal';

interface KpiCreationModalProps {
  onClose: () => void;
}

export default function KpiCreationModal({onClose}: KpiCreationModalProps): JSX.Element {
  const [selectedKpi, setSelectedKpi] = useState<KpiTemplate>();
  const [definition, setDefinition] = useState<{
    key: string;
    versions: string[];
    tenantIds: string[];
  }>({
    key: '',
    versions: [],
    tenantIds: [],
  });
  const history = useHistory();
  const {pathname} = useLocation();
  const {mightFail} = useErrorHandling();
  const kpiTemplates = [automationRate()];

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
              label: t('report.kpiTemplates.create'),
              kind: 'primary',
              disabled: !definition.key || !selectedKpi,
              onClick: async () => {
                if (!selectedKpi) {
                  return;
                }

                const collectionId = getCollection(pathname);
                await mightFail(
                  createEntity('report/process/single', {
                    collectionId,
                    name: selectedKpi.name,
                    description: selectedKpi.description,
                    data: {
                      ...selectedKpi.config,
                      configuration: {
                        ...newReport.new.data.configuration,
                        targetValue: {
                          ...newReport.new.data.configuration.targetValue,
                          active: true,
                          isKpi: true,
                        },
                      },
                      definitions: [
                        {
                          identifier: 'definition',
                          ...definition,
                        },
                      ],
                    },
                  }),
                  (id) => history.push(`report/${id}/`),
                  showError
                );
              },
            },
          },
        },
      ]}
    />
  );
}
