/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {memo, useRef, useState} from 'react';
import {Form, Field} from 'react-final-form';
import {useSearchParams} from 'react-router-dom';
import {Button, Dropdown, OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {SortAscending, Checkmark, Filter} from '@carbon/react/icons';
import {taskFilters} from 'modules/constants/taskFilters';
import {tracking} from 'modules/tracking';
import {useTaskFilters, TaskFilters} from 'modules/hooks/useTaskFilters';
import {CustomFiltersModal} from './CustomFiltersModal';
import {getStateLocally} from 'modules/utils/localStorage';
import {prepareCustomFiltersParams} from 'modules/custom-filters/prepareCustomFiltersParams';
import difference from 'lodash/difference';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import styles from './styles.module.scss';
import cn from 'classnames';

type FormValues = Pick<TaskFilters, 'filter' | 'sortBy'>;

type Props = {
  disabled: boolean;
};

const SORTING_OPTIONS: Record<TaskFilters['sortBy'], string> = {
  creation: 'Creation date',
  'follow-up': 'Follow-up date',
  due: 'Due date',
  completion: 'Completion date',
};
const SORTING_OPTIONS_ORDER: TaskFilters['sortBy'][] = [
  'creation',
  'due',
  'follow-up',
];
const COMPLETED_SORTING_OPTIONS_ORDER: TaskFilters['sortBy'][] = [
  'creation',
  'due',
  'follow-up',
  'completion',
];
const CUSTOM_FILTER_ITEM = {id: 'custom', text: 'Custom filter'} as const;
const CUSTOM_FILTERS_PARAMS = [
  'state',
  'followUpDateFrom',
  'followUpDateTo',
  'dueDateFrom',
  'dueDateTo',
  'assigned',
  'assignee',
  'taskDefinitionId',
  'candidateGroup',
  'candidateUser',
  'processDefinitionKey',
  'processInstanceKey',
  'tenantIds',
  'taskVariables',
] as const;

const Filters: React.FC<Props> = memo(({disabled}) => {
  const {data: currentUser} = useCurrentUser();
  const [searchParams, setSearchParams] = useSearchParams();
  const {filter, sortBy} = useTaskFilters();
  const dropdownRef = useRef<null | HTMLButtonElement>(null);
  const initialValues = {filter, sortBy};
  const sortOptionsOrder = ['completed', 'custom'].includes(filter)
    ? COMPLETED_SORTING_OPTIONS_ORDER
    : SORTING_OPTIONS_ORDER;
  const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] =
    useState(false);
  const customFilters = getStateLocally('customFilters')?.custom;

  return (
    <section className={styles.container} aria-label="Filters">
      <Form<FormValues>
        onSubmit={(values) => {
          const customFilters = getStateLocally('customFilters')?.custom;
          const customFiltersParams =
            values.filter === 'custom' && customFilters !== undefined
              ? prepareCustomFiltersParams(
                  customFilters,
                  currentUser?.userId ?? '',
                )
              : {};

          const updatedParams =
            Object.keys(customFiltersParams).length > 0
              ? new URLSearchParams({
                  ...searchParams,
                  ...values,
                  ...customFiltersParams,
                })
              : new URLSearchParams({
                  ...searchParams,
                  ...values,
                });

          const paramsToDelete = difference(
            CUSTOM_FILTERS_PARAMS,
            Object.keys(customFiltersParams),
          );

          paramsToDelete.forEach((param) => {
            updatedParams.delete(param);
          });

          if (values.filter !== 'custom') {
            CUSTOM_FILTERS_PARAMS.forEach((param) => {
              updatedParams.delete(param);
            });
          }

          tracking.track({
            eventName: 'tasks-filtered',
            filter: values.filter,
            sorting: values.sortBy,
            customFilters: Object.keys(customFilters ?? {}),
            customFilterVariableCount: customFilters?.variables?.length ?? 0,
          });

          setSearchParams(updatedParams);
        }}
        initialValues={initialValues}
      >
        {({handleSubmit, form}) => (
          <>
            <form
              className={cn(styles.form, styles.customFilters)}
              onSubmit={handleSubmit}
            >
              <Field<FormValues['filter']> name="filter">
                {({input}) => (
                  <Dropdown<{id: TaskFilters['filter']; text: string}>
                    ref={dropdownRef}
                    id={input.name}
                    hideLabel
                    titleText="Filter options"
                    label="Filter options"
                    items={
                      customFilters === undefined
                        ? Object.values(taskFilters)
                        : [...Object.values(taskFilters), CUSTOM_FILTER_ITEM]
                    }
                    itemToString={(item) => (item ? item.text : '')}
                    disabled={disabled}
                    onChange={(event) => {
                      const newFilter = event.selectedItem?.id;

                      if (typeof newFilter !== 'string') {
                        return;
                      }

                      if (newFilter === 'completed') {
                        form.change('sortBy', 'completion');
                      }

                      if (
                        newFilter !== 'completed' &&
                        sortBy === 'completion'
                      ) {
                        form.change('sortBy', 'creation');
                      }

                      input.onChange(newFilter);
                      form.submit();
                      dropdownRef.current?.focus();
                    }}
                    selectedItem={
                      input.value === 'custom'
                        ? CUSTOM_FILTER_ITEM
                        : taskFilters[input.value]
                    }
                    onBlur={input.onBlur}
                    onFocus={input.onFocus}
                    size="md"
                  />
                )}
              </Field>
              <Button
                hasIconOnly
                iconDescription={CUSTOM_FILTER_ITEM.text}
                renderIcon={Filter}
                kind="ghost"
                size="md"
                onClick={() => setIsCustomFiltersModalOpen(true)}
                tooltipPosition="bottom"
              />
              <Field<FormValues['sortBy']> name="sortBy">
                {({input}) => (
                  <OverflowMenu
                    aria-label="Sort tasks"
                    iconDescription="Sort tasks"
                    renderIcon={SortAscending}
                    size="md"
                    onFocus={input.onFocus}
                    onBlur={input.onBlur}
                    disabled={disabled}
                    menuOptionsClass="cds--custom-sorting-menu-options-wrapper"
                    align="bottom"
                  >
                    {sortOptionsOrder.map((id) => (
                      <OverflowMenuItem
                        key={id}
                        itemText={
                          <div className={styles.sortItem}>
                            <Checkmark
                              aria-label=""
                              size={20}
                              style={{
                                visibility:
                                  input.value === id ? undefined : 'hidden',
                              }}
                            />
                            <span className={styles.menuItem}>
                              {SORTING_OPTIONS[id]}
                            </span>
                          </div>
                        }
                        onClick={() => {
                          input.onChange(id);
                          form.submit();
                        }}
                      />
                    ))}
                  </OverflowMenu>
                )}
              </Field>
            </form>
            <CustomFiltersModal
              isOpen={isCustomFiltersModalOpen}
              onClose={() => {
                setIsCustomFiltersModalOpen(false);
              }}
              onApply={() => {
                setIsCustomFiltersModalOpen(false);
                form.change('filter', 'custom');
                form.submit();
              }}
            />
          </>
        )}
      </Form>
    </section>
  );
});

export {Filters};
