/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode, useEffect, useState} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';

import {
  Modal,
  Button as LegacyButton,
  ButtonGroup,
  Labeled,
  Form,
  UserTypeahead,
  User,
} from 'components';
import {t} from 'translation';
import {showError} from 'notifications';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {FilterProps} from '../types';

import {loadUsersByDefinition, loadUsersByReportIds, getUsersById} from './service';

import './AssigneeFilter.scss';

interface AssigneeFilterProps
  extends WithErrorHandlingProps,
    FilterProps<{
      values?: (string | null)[];
      operator?: string;
    }> {
  filterType: 'assignee' | 'candidateGroup';
  filterLevel: 'view';
  forceEnabled?: (users: User[], operator?: string) => boolean;
  getPretext?: (users: User[], operator?: string) => ReactNode;
  getPosttext?: (users: User[], operator?: string) => ReactNode;
}

export function AssigneeFilter(props: AssigneeFilterProps) {
  const {
    filterData,
    close,
    definitions,
    reportIds,
    getPretext,
    getPosttext,
    className,
    forceEnabled,
    mightFail,
    filterType,
    addFilter,
  } = props;
  const validDefinitions = definitions?.filter(
    (definition) => definition.versions?.length && definition.tenantIds?.length
  );

  const [users, setUsers] = useState<User[]>([]);
  const [operator, setOperator] = useState<string | undefined>('in');
  const [applyTo, setApplyTo] = useState(
    validDefinitions?.find(({identifier}) => filterData?.appliedTo[0] === identifier) ||
      validDefinitions?.[0]
  );

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
              type: 'user',
            },
          });
        }
        if (existingUsers.length > 0) {
          const users: User[] = await mightFail(
            getUsersById(filterType, existingUsers),
            (users: User[]) =>
              users.map((user) => ({
                id: `${user.type?.toUpperCase()}:${user.id}`,
                identity: user,
              })),
            showError
          );
          combined.push(...users);
        }

        setUsers(combined);
      }
    })();
  }, [filterData, filterType, mightFail]);

  const confirm = () => {
    addFilter({
      type: filterType,
      data: {operator, values: users.map((user) => user.identity.id)},
      appliedTo: [applyTo?.identifier].filter((definition): definition is string => !!definition),
    });
  };

  return (
    <Modal
      open
      onClose={close}
      className={classnames('AssigneeFilter', className)}
      isOverflowVisible
    >
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      </Modal.Header>
      <Modal.Content>
        {!reportIds && (
          <FilterSingleDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={(applyTo) => {
              setApplyTo(applyTo);
              setUsers([]);
            }}
          />
        )}
        {getPretext?.(users, operator)}
        <ButtonGroup>
          <LegacyButton active={operator === 'in'} onClick={() => setOperator('in')}>
            {t('common.filter.assigneeModal.includeOnly')}
          </LegacyButton>
          <LegacyButton active={operator === 'not in'} onClick={() => setOperator('not in')}>
            {t('common.filter.assigneeModal.excludeOnly')}
          </LegacyButton>
        </ButtonGroup>
        <Form>
          <Form.InputGroup>
            <Labeled label={t(`common.filter.assigneeModal.type.${filterType}`)}>
              <UserTypeahead
                users={users}
                onChange={setUsers}
                fetchUsers={async (query) => {
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
                }}
                optionsOnly
              />
            </Labeled>
          </Form.InputGroup>
        </Form>
        {getPosttext?.(users, operator)}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button
          className="confirm"
          disabled={users.length === 0 && !forceEnabled?.(users, operator)}
          onClick={confirm}
        >
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(AssigneeFilter);
