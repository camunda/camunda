/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {Modal, Button, ButtonGroup, Labeled, Form, MultiSelect} from 'components';
import {loadUsers} from './service';
import {t} from 'translation';
import update from 'immutability-helper';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import './AssigneeFilter.scss';

export function AssigneeFilter({
  filterData,
  close,
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  mightFail,
  filterType,
  addFilter,
}) {
  const [values, setValues] = useState([]);
  const [data, setData] = useState({operator: 'in', values: []});

  useEffect(() => {
    if (filterData) {
      setData(filterData.data);
    }
  }, [filterData]);

  useEffect(() => {
    mightFail(
      loadUsers(filterType, {processDefinitionKey, processDefinitionVersions, tenantIds}),
      setValues,
      showError
    );
  }, [mightFail, processDefinitionKey, processDefinitionVersions, tenantIds, filterType]);

  const addValue = (value) => setData(update(data, {values: {$push: [value]}}));

  const removeValue = (value) => {
    const newData = update(data, {
      values: {$set: data.values.filter((val) => val !== value)},
    });
    setData(newData);
  };

  const confirm = () => addFilter({type: filterType, data});

  return (
    <Modal open onClose={close} className="AssigneeFilter">
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      </Modal.Header>
      <Modal.Content>
        <ButtonGroup>
          <Button
            active={data.operator === 'in'}
            onClick={() => setData(update(data, {operator: {$set: 'in'}}))}
          >
            {t('common.filter.assigneeModal.includeOnly')}
          </Button>
          <Button
            active={data.operator === 'not in'}
            onClick={() => setData(update(data, {operator: {$set: 'not in'}}))}
          >
            {t('common.filter.assigneeModal.excludeOnly')}
          </Button>
        </ButtonGroup>
        <Form>
          <Form.InputGroup>
            <Labeled label={t(`common.filter.assigneeModal.type.${filterType}`)}>
              <MultiSelect
                values={data.values.map((value) => ({
                  value,
                  label: value === null ? t('common.filter.assigneeModal.unassigned') : value,
                }))}
                onAdd={addValue}
                onRemove={removeValue}
                onClear={() => setData(update(data, {values: {$set: []}}))}
                placeholder={t('common.filter.assigneeModal.selectValue')}
              >
                {!data.values.includes(null) && (
                  <MultiSelect.Option key={null} value={null}>
                    {t('common.filter.assigneeModal.unassigned')}
                  </MultiSelect.Option>
                )}
                {values
                  ?.filter((val) => !data.values.includes(val))
                  .map((val) => (
                    <MultiSelect.Option key={val} value={val}>
                      {val}
                    </MultiSelect.Option>
                  ))}
              </MultiSelect>
            </Labeled>
          </Form.InputGroup>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary onClick={confirm} disabled={data.values.length === 0}>
          {filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(AssigneeFilter);
