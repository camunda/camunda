/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import {Modal, Button, Form, LabeledInput} from 'components';

import {t} from 'translation';
import getMapping from './options';

import './StateFilter.scss';

export default function StateFilter({addFilter, close, filterType}) {
  const [selectedOption, setSelectedOption] = useState();
  const options = getMapping(filterType);

  function createFilter() {
    const type = options.mappings[selectedOption].key;
    addFilter({type});
  }

  const isFilterValid = typeof selectedOption !== 'undefined';

  return (
    <Modal
      open
      onClose={close}
      onConfirm={isFilterValid ? createFilter : undefined}
      className="StateFilter"
    >
      <Modal.Header>{options?.modalTitle}</Modal.Header>
      <Modal.Content>
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
