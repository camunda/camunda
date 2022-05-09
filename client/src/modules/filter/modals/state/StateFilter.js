/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {Modal, Button, Form, LabeledInput} from 'components';
import {t} from 'translation';

import FilterDefinitionSelection from '../FilterDefinitionSelection';

import getMapping from './options';

import './StateFilter.scss';

export default function StateFilter({addFilter, close, filterType, definitions}) {
  const [selectedOption, setSelectedOption] = useState();
  const [applyTo, setApplyTo] = useState([
    {identifier: 'all', displayName: t('common.filter.definitionSelection.allProcesses')},
  ]);

  const options = getMapping(filterType);

  function createFilter() {
    const type = options.mappings[selectedOption].key;
    addFilter({type, appliedTo: applyTo.map(({identifier}) => identifier)});
  }

  const isFilterValid = typeof selectedOption !== 'undefined' && applyTo.length > 0;

  return (
    <Modal
      open
      onClose={close}
      onConfirm={isFilterValid ? createFilter : undefined}
      className="StateFilter"
    >
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
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!isFilterValid} onClick={createFilter}>
          {t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
