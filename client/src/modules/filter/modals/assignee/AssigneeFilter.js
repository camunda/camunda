/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {Modal, Button, ButtonGroup, Labeled, Form, UserTypeahead} from 'components';
import {loadUsers, getUsersById} from './service';
import {t} from 'translation';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import './AssigneeFilter.scss';

export function AssigneeFilter({
  filterData,
  close,
  processDefinitionKey,
  tenantIds,
  mightFail,
  filterType,
  addFilter,
}) {
  const [users, setUsers] = useState([]);
  const [operator, setOperator] = useState('in');

  useEffect(() => {
    (async () => {
      if (filterData) {
        setOperator(filterData.data.operator);

        const hasUnassigned = filterData.data.values.includes(null);
        const existingUsers = filterData.data.values.filter((id) => !!id);
        const combined = [];

        if (hasUnassigned) {
          combined.push({
            id: 'USER:null',
            identity: {id: null, name: t('common.filter.assigneeModal.unassigned'), type: 'user'},
          });
        }
        if (existingUsers.length > 0) {
          const users = await mightFail(
            getUsersById(filterType, existingUsers),
            (users) =>
              users.map((user) => ({id: `${user.type.toUpperCase()}:${user.id}`, identity: user})),
            showError
          );
          combined.push(...users);
        }

        setUsers(combined);
      }
    })();
  }, [filterData, filterType, mightFail]);

  const confirm = () => {
    addFilter({type: filterType, data: {operator, values: users.map((user) => user.identity.id)}});
  };

  return (
    <Modal open onClose={close} className="AssigneeFilter">
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      </Modal.Header>
      <Modal.Content>
        <ButtonGroup>
          <Button active={operator === 'in'} onClick={() => setOperator('in')}>
            {t('common.filter.assigneeModal.includeOnly')}
          </Button>
          <Button active={operator === 'not in'} onClick={() => setOperator('not in')}>
            {t('common.filter.assigneeModal.excludeOnly')}
          </Button>
        </ButtonGroup>
        <Form>
          <Form.InputGroup>
            <Labeled label={t(`common.filter.assigneeModal.type.${filterType}`)}>
              <UserTypeahead
                users={users}
                onChange={setUsers}
                fetchUsers={async (query) => {
                  const result = await mightFail(
                    loadUsers(filterType, {
                      processDefinitionKey,
                      tenantIds,
                      terms: query,
                    }),
                    (result) => result,
                    showError
                  );

                  if ('unassigned'.indexOf(query.toLowerCase()) !== -1) {
                    result.total++;
                    result.result.unshift({
                      id: null,
                      name: t('common.filter.assigneeModal.unassigned'),
                      type: 'user',
                    });
                  }

                  return result;
                }}
                optionsOnly
              />
            </Labeled>
          </Form.InputGroup>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary onClick={confirm} disabled={users.length === 0}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(AssigneeFilter);
