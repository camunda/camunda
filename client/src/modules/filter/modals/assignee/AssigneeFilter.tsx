/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode, useCallback, useEffect, useState} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';

import {Modal, Button as LegacyButton, ButtonGroup, UserTypeahead, User, Form} from 'components';
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
  forceEnabled?: () => boolean;
  getPretext?: () => ReactNode;
  getPosttext?: () => ReactNode;
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

  const [users, setUsers] = useState<User[] | null>(null);
  const [selectionResetIdx, setSelectionResetIdx] = useState(0);
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
              resetUserSelection();
            }}
          />
        )}
        {getPretext?.()}
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
            <UserTypeahead
              // The carbon multi-select cannot be controlled from outside
              // Remove this when this issue get resolved: https://github.com/carbon-design-system/carbon/issues/10340
              key={selectionResetIdx}
              titleText={t(`common.filter.assigneeModal.type.${filterType}`)}
              users={users}
              onChange={setUsers}
              fetchUsers={fetchUsers}
              optionsOnly
            />
          </Form.InputGroup>
        </Form>
        {getPosttext?.()}
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

export default withErrorHandling(AssigneeFilter);
