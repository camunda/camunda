/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Form, FormGroup, RadioButton, RadioButtonGroup} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';
import {
  Definition,
  FlowNodeStateFilterType,
  IncidentFilterType,
  InstanceStateFilterType,
} from 'types';

import FilterDefinitionSelection from '../FilterDefinitionSelection';
import {FilterProps} from '../types';

import getMapping from './options';

export default function StateFilter({
  addFilter,
  close,
  filterType,
  definitions,
}: FilterProps<InstanceStateFilterType | FlowNodeStateFilterType | IncidentFilterType>) {
  const [selectedOption, setSelectedOption] = useState<number>(0);
  const [applyTo, setApplyTo] = useState<Definition[]>([
    {identifier: 'all', displayName: t('common.filter.definitionSelection.allProcesses')},
  ]);

  const options = getMapping(filterType);

  function createFilter() {
    const type = options?.mappings[selectedOption]?.key;
    if (type) {
      addFilter({type, appliedTo: applyTo.map(({identifier}) => identifier), data: undefined});
    }
  }

  const isFilterValid = typeof selectedOption !== 'undefined' && applyTo.length > 0;

  return (
    <Modal size="sm" open onClose={close} className="StateFilter">
      <Modal.Header title={options?.modalTitle} />
      <Modal.Content>
        <FilterDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        <Form>
          <FormGroup legendText={options?.pretext.toString()}>
            <RadioButtonGroup name="instanceState" orientation="vertical">
              {options?.mappings.map(({key, label}, idx) => (
                <RadioButton
                  key={key}
                  value={idx}
                  labelText={label}
                  checked={selectedOption === idx}
                  onClick={() => setSelectedOption(idx)}
                />
              ))}
            </RadioButtonGroup>
          </FormGroup>
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
