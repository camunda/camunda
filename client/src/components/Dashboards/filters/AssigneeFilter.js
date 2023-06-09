/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {CarbonPopover, Form, Switch, Button, Icon, UserTypeahead} from 'components';
import {withErrorHandling} from 'HOC';
import {AssigneeFilterPreview} from 'filter';

import {getAssigneeNames, loadUsersByReportIds} from './service';

import './AssigneeFilter.scss';

function getOperatorText(operator) {
  switch (operator) {
    case 'not in':
      return t('common.filter.list.operators.not');
    default:
      return t('common.filter.list.operators.is');
  }
}

export function AssigneeFilter({
  config,
  setFilter,
  reports,
  mightFail,
  filter,
  type,
  children,
  resetTrigger,
}) {
  const [users, setUsers] = useState([]);
  const [names, setNames] = useState({});

  const {operator, values, defaultValues, allowCustomValues} = config;

  useEffect(() => {
    const additionalValues = (defaultValues ?? []).filter((value) => !values.includes(value));
    const allRequiredNames = [...values, ...additionalValues];

    mightFail(getAssigneeNames(type, allRequiredNames), (names) => {
      setNames(
        names.reduce((obj, assignee) => {
          obj[assignee.id] = assignee;
          return obj;
        }, {})
      );
      setUsers(
        additionalValues.map((additionalValue) => {
          if (additionalValue === null) {
            return {
              id: 'USER:null',
              identity: {name: t('common.filter.assigneeModal.unassigned'), type: 'user'},
            };
          } else {
            const identity = names.find(({id}) => id === additionalValue);
            const newId = `${identity.type.toUpperCase()}:${additionalValue}`;
            return {id: newId, identity};
          }
        })
      );
    });
  }, [mightFail, values, defaultValues, type, resetTrigger]);

  function addValue(value, scopedFilter = filter) {
    const newFilter = {
      operator,
      values: [...(scopedFilter?.values || []), value],
    };
    setFilter(newFilter);

    return newFilter;
  }

  function removeValue(value, scopedFilter = filter) {
    const values = scopedFilter.values.filter((existingValue) => existingValue !== value);

    const newFilter = values.length ? {operator, values} : null;
    setFilter(newFilter);

    return newFilter;
  }

  const predefinedUsers = filter?.values.filter((user) => values.includes(user)) ?? [];
  function addCustomValues(users) {
    setFilter({
      operator,
      values: [...predefinedUsers, ...users.map((user) => user.identity.id)],
    });
  }
  function removeCustomValues() {
    if (predefinedUsers.length) {
      setFilter({
        operator,
        values: predefinedUsers,
      });
    } else {
      setFilter(null);
    }
  }

  let previewFilter = filter;
  if (filter?.values.length > 1) {
    previewFilter = {operator: filter.operator, values: [t('dashboard.filter.multiple')]};
  }

  return (
    <div className="AssigneeFilter__Dashboard">
      <div className="title">
        {t('common.filter.types.' + type)}
        {children}
      </div>
      <CarbonPopover
        title={
          <>
            <Icon type="filter" className={classnames('indicator', {active: filter})} />{' '}
            {filter ? (
              <AssigneeFilterPreview
                filter={{type, data: previewFilter}}
                getNames={() => Object.values(names)}
              />
            ) : (
              getOperatorText(operator) + ' ...'
            )}
          </>
        }
      >
        <Form compact>
          <fieldset>
            {values.map((value, idx) => (
              <Switch
                key={idx}
                checked={!!filter?.values.includes(value)}
                label={
                  value === null
                    ? t('common.filter.assigneeModal.unassigned')
                    : names[value]?.name || value
                }
                onChange={({target}) => {
                  if (target.checked) {
                    addValue(value);
                  } else {
                    removeValue(value);
                  }
                }}
              />
            ))}
            {allowCustomValues && (
              <div className="customValue">
                <Switch
                  checked={!!filter?.values.some((user) => !values.includes(user))}
                  onChange={(evt) => {
                    if (evt.target.checked) {
                      addCustomValues(users);
                    } else {
                      removeCustomValues();
                    }
                  }}
                  disabled={!users.length}
                />
                <UserTypeahead
                  users={users}
                  onChange={(users) => {
                    setUsers(users);

                    if (users.length) {
                      addCustomValues(users);
                      setNames({
                        ...names,
                        ...users.reduce((obj, {identity}) => {
                          obj[identity.id] = identity;
                          return obj;
                        }, {}),
                      });
                    } else {
                      removeCustomValues();
                    }
                  }}
                  fetchUsers={async (query) => {
                    const result = await mightFail(
                      loadUsersByReportIds(type, {
                        reportIds: reports.map(({id}) => id).filter((id) => !!id),
                        terms: query,
                      }),
                      (result) => result
                    );

                    result.result = result.result.filter((user) => !values.includes(user.id));

                    if (
                      'unassigned'.indexOf(query.toLowerCase()) !== -1 &&
                      !values.includes(null)
                    ) {
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
              </div>
            )}
          </fieldset>
          <hr />
          <Button className="reset-button" disabled={!filter} onClick={() => setFilter()}>
            {t('common.off')}
          </Button>
        </Form>
      </CarbonPopover>
    </div>
  );
}

export default withErrorHandling(AssigneeFilter);
