/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComboBox} from '@carbon/react';

import {t} from 'translation';
import {Definition} from 'types';

import './FilterSingleDefinitionSelection.scss';

interface FilterSingleDefinitionSelectionProps {
  availableDefinitions: Definition[];
  applyTo?: Definition | null;
  setApplyTo: (definition?: Definition) => void;
}

export default function FilterSingleDefinitionSelection({
  availableDefinitions,
  applyTo,
  setApplyTo,
}: FilterSingleDefinitionSelectionProps) {
  if (availableDefinitions.length <= 1) {
    return null;
  }

  return (
    <div className="FilterSingleDefinitionSelection">
      <ComboBox
        titleText={t('common.definitionSelection.select.process')}
        id="definition-selector"
        items={availableDefinitions.filter(
          (definition) => definition.versions?.length && definition.tenantIds?.length
        )}
        itemToString={(definition) =>
          (definition?.displayName || definition?.name || definition?.key)?.toString() || ''
        }
        selectedItem={applyTo}
        placeholder={t('dashboard.addButton.selectReportPlaceholder').toString()}
        onChange={({selectedItem}) => selectedItem && setApplyTo(selectedItem)}
      />
    </div>
  );
}
