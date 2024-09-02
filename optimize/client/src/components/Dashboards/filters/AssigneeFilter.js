/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {Filter} from '@carbon/icons-react';
import {Button, Form, FormGroup, Stack, Toggle} from '@carbon/react';
import classnames from 'classnames';

import {t} from 'translation';
import {Popover, UserTypeahead} from 'components';
import {useErrorHandling} from 'hooks';
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

export default function AssigneeFilter({
  config,
  setFilter,
  reports,
  filter,
  type,
  children,
  resetTrigger,
}) {
  const [users, setUsers] = useState([]);
  const [names, setNames] = useState({});
  const {mightFail} = useErrorHandling();

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
    const values = scopedFilter?.values.filter((existingValue) => existingValue !== value);

    const newFilter = values?.length ? {operator, values} : null;
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

  async function fetchUsers(query) {
    const result = await mightFail(
      loadUsersByReportIds(type, {
        reportIds: reports.map(({id}) => id).filter((id) => !!id),
        terms: query,
      }),
      (result) => result
    );

    result.result = result.result.filter((user) => !values.includes(user.id));

    if ('unassigned'.indexOf(query.toLowerCase()) !== -1 && !values.includes(null)) {
      result.total++;
      result.result.unshift({
        id: null,
        name: t('common.filter.assigneeModal.unassigned'),
        type: 'user',
      });
    }

    return result;
  }

  return (
    <div className="AssigneeFilter__Dashboard">
      <div className="title">
        {t('common.filter.types.' + type)}
        {children}
      </div>
      <Popover
        isTabTip
        trigger={
          <Popover.ListBox size="sm">
            <Filter className={classnames('indicator', {active: filter})} />
            {filter ? (
              <AssigneeFilterPreview
                filter={{type, data: previewFilter}}
                getNames={() => Object.values(names)}
              />
            ) : (
              getOperatorText(operator) + ' ...'
            )}
          </Popover.ListBox>
        }
      >
        <Form>
          <FormGroup legendText={t('common.filter.types.' + type)}>
            <Stack gap={4}>
              {values.map((value, idx) => {
                const label =
                  value === null
                    ? t('common.filter.assigneeModal.unassigned').toString()
                    : names[value]?.name || value;
                return (
                  <Toggle
                    key={idx}
                    id={`assignee-${idx}`}
                    size="sm"
                    toggled={!!filter?.values.includes(value)}
                    labelText={label}
                    hideLabel
                    onToggle={(checked) => {
                      if (checked) {
                        addValue(value);
                      } else {
                        removeValue(value);
                      }
                    }}
                  />
                );
              })}
              {allowCustomValues && (
                <Stack gap={4} className="customValue">
                  <Toggle
                    id="customValue"
                    size="sm"
                    labelText={t('common.filter.assignee.allowCustomValues')}
                    hideLabel
                    toggled={!!filter?.values.some((user) => !values.includes(user))}
                    onToggle={(checked) => {
                      if (checked) {
                        addCustomValues(users);
                      } else {
                        removeCustomValues();
                      }
                    }}
                    disabled={!users.length}
                  />
                  <UserTypeahead
                    key={users.length ? 'hasUsers' : 'noUsers'}
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
                    fetchUsers={fetchUsers}
                    optionsOnly
                  />
                </Stack>
              )}
            </Stack>
          </FormGroup>
          <hr />
          <Button
            size="sm"
            kind="ghost"
            className="reset-button"
            disabled={!filter}
            onClick={() => {
              setFilter();
              setUsers([]);
            }}
          >
            {t('common.off')}
          </Button>
        </Form>
      </Popover>
    </div>
  );
}
