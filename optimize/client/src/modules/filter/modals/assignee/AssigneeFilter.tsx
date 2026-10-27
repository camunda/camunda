/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import classnames from 'classnames';
import {Button, Form, FormGroup, RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {Modal, UserTypeahead, User} from 'components';
import {t} from 'translation';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {FilterProps} from '../types';

import {loadUsersByDefinition, loadUsersByReportIds, getUsersById} from './service';

export default function AssigneeFilter(props: FilterProps<'assignee'>) {
  const {
    filterData,
    close,
    definitions,
    reportIds,
    getPretext,
    getPosttext,
    className,
    forceEnabled,
    filterType,
    addFilter,
  } = props;
  const validDefinitions = definitions?.filter(
    (definition) => definition.versions?.length && definition.tenantIds?.length
  );

  const [users, setUsers] = useState<User[] | null>(null);
  const [selectionResetIdx, setSelectionResetIdx] = useState(0);
  const [operator, setOperator] = useState<string | undefined>('in');
  const [applyTo, setApplyTo] = useState(
    validDefinitions?.find(({identifier}) => filterData?.appliedTo[0] === identifier) ||
      validDefinitions?.[0]
  );
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    (async () => {
      if (filterData) {
        setOperator(filterData.data.operator);

        const hasUnassigned = filterData.data.values?.includes(null);
        const existingUsers = filterData.data.values?.filter((id) => !!id) || [];
        const combined: User[] = [];

        if (hasUnassigned) {
          combined.push({
            id: 'USER:null',
            identity: {
              id: null,
              name: t('common.filter.assigneeModal.unassigned').toString(),
            },
          });
        }
        if (existingUsers.length > 0) {
          const users: User[] | undefined = await mightFail(
            getUsersById(filterType, existingUsers),
            (users: User[]) =>
              users.map(
                (user) =>
                  ({
                    id: `${user.type?.toUpperCase()}:${user.id}`,
                    identity: user,
                  }) as unknown as User
              ),
            showError
          );
          combined.push(...(users || []));
        }

        setUsers(combined);
      } else {
        setUsers([]);
      }
    })();
  }, [filterData, filterType, mightFail]);

  const confirm = () => {
    if (users) {
      addFilter({
        type: filterType,
        data: {operator, values: users.map((user) => user.identity.id)},
        appliedTo: [applyTo?.identifier].filter((definition): definition is string => !!definition),
      });
    }
  };

  const fetchUsers = useCallback(
    async (query: string) => {
      let dataPromise;
      if (reportIds) {
        dataPromise = loadUsersByReportIds(filterType, {
          reportIds,
          terms: query,
        });
      } else {
        dataPromise = loadUsersByDefinition(filterType, {
          processDefinitionKey: applyTo?.key,
          tenantIds: applyTo?.tenantIds,
          terms: query,
        });
      }
      const result = await mightFail(dataPromise, (result) => result, showError);

      if ('unassigned'.indexOf(query.toLowerCase()) !== -1) {
        result.total++;
        result.result.unshift({
          id: null,
          name: t('common.filter.assigneeModal.unassigned'),
          type: 'user',
        });
      }

      return result;
    },
    [applyTo?.key, applyTo?.tenantIds, filterType, mightFail, reportIds]
  );

  function resetUserSelection() {
    setUsers([]);
    setSelectionResetIdx((key) => key + 1);
  }

  return (
    <Modal
      open
      onClose={close}
      className={classnames('AssigneeFilter', className)}
      isOverflowVisible
    >
      <Modal.Header
        title={t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`).toString(),
        })}
      />
      <Modal.Content>
        <Stack gap={6}>
          {!reportIds && (
            <FilterSingleDefinitionSelection
              availableDefinitions={definitions}
              applyTo={applyTo}
              setApplyTo={(applyTo) => {
                setApplyTo(applyTo);
                resetUserSelection();
              }}
            />
          )}
          {getPretext?.()}
          <RadioButtonGroup
            name="include-exclude"
            onChange={(selected) => setOperator(selected as string)}
          >
            <RadioButton
              labelText={t('common.filter.assigneeModal.includeOnly')}
              checked={operator === 'in'}
              value="in"
            />
            <RadioButton
              labelText={t('common.filter.assigneeModal.excludeOnly')}
              checked={operator === 'not in'}
              value="not in"
            />
          </RadioButtonGroup>
          <Form>
            <FormGroup legendText={t(`common.filter.assigneeModal.type.${filterType}`)}>
              <UserTypeahead
                // The carbon multi-select cannot be controlled from outside
                // Remove this when this issue get resolved: https://github.com/carbon-design-system/carbon/issues/10340
                key={selectionResetIdx}
                users={users}
                onChange={setUsers}
                fetchUsers={fetchUsers}
                optionsOnly
              />
            </FormGroup>
          </Form>
          {getPosttext?.()}
        </Stack>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button
          className="confirm"
          disabled={(!users || users.length === 0) && !forceEnabled?.()}
          onClick={confirm}
        >
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
