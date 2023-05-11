/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, Form, LabeledInput} from 'components';
import {t} from 'translation';
import {Definition, FilterData} from 'types';

import FilterDefinitionSelection from '../FilterDefinitionSelection';
import {FilterProps} from '../types';

import getMapping from './options';

import './StateFilter.scss';

interface StateFilterProps extends FilterProps<FilterData | undefined> {
  filterType: 'instanceState' | 'incident' | 'incidentInstances' | 'flowNodeStatus';
}

export default function StateFilter({addFilter, close, filterType, definitions}: StateFilterProps) {
  const [selectedOption, setSelectedOption] = useState<number>(0);
  const [applyTo, setApplyTo] = useState<Definition[]>([
    {identifier: 'all', displayName: t('common.filter.definitionSelection.allProcesses')},
  ]);

  const options = getMapping(filterType);

  function createFilter() {
    const type = options?.mappings[selectedOption]?.key;
    type &&
      addFilter({type, appliedTo: applyTo.map(({identifier}) => identifier), data: undefined});
  }

  const isFilterValid = typeof selectedOption !== 'undefined' && applyTo.length > 0;

  return (
    <Modal size="sm" open onClose={close} className="StateFilter">
      <Modal.Header>{options?.modalTitle}</Modal.Header>
      <Modal.Content>
        <FilterDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        <p className="description">{options?.pretext}</p>
        <Form>
          <fieldset>
            {options?.mappings.map(({key, label}, idx) => (
              <LabeledInput
                key={key}
                type="radio"
                label={label}
                checked={selectedOption === idx}
                onChange={() => setSelectedOption(idx)}
              />
            ))}
          </fieldset>
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" disabled={!isFilterValid} onClick={createFilter}>
          {t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
