/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form, Field} from 'react-final-form';
import {useSearchParams} from 'react-router-dom';
import {
  Container,
  FormElement,
  MenuItemWrapper,
  MenuOptionsStyles,
  SortItemContainer,
  MENU_OPTIONS_STYLES_CLASSNAME,
} from './styled';
import {taskFilters} from 'modules/constants/taskFilters';
import {tracking} from 'modules/tracking';
import {Dropdown, OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {SortAscending, Checkmark} from '@carbon/react/icons';
import {useRef} from 'react';
import {useTaskFilters, TaskFilters} from 'modules/hooks/useTaskFilters';

type FormValues = Pick<TaskFilters, 'filter' | 'sortBy'>;

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

type Props = {
  disabled: boolean;
};

const Filters: React.FC<Props> = ({disabled}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {filter, sortBy} = useTaskFilters();
  const dropdownRef = useRef<null | HTMLButtonElement>(null);
  const initialValues = {filter, sortBy};
  const sortOptionsOrder =
    filter === 'completed'
      ? COMPLETED_SORTING_OPTIONS_ORDER
      : SORTING_OPTIONS_ORDER;

  return (
    <Container aria-label="Filters">
      <MenuOptionsStyles />
      <Form<FormValues>
        onSubmit={(values) => {
          const updatedParams = new URLSearchParams(
            Object.assign(
              Object.fromEntries(new URLSearchParams(searchParams).entries()),
              values,
            ),
          );

          tracking.track({
            eventName: 'tasks-filtered',
            filter: values.filter,
            sorting: values.sortBy,
          });

          setSearchParams(updatedParams);
        }}
        initialValues={initialValues}
      >
        {({handleSubmit, form}) => (
          <FormElement onSubmit={handleSubmit}>
            <Field<FormValues['filter']> name="filter">
              {({input}) => (
                <Dropdown<{id: TaskFilters['filter']; text: string}>
                  ref={dropdownRef}
                  id={input.name}
                  titleText="Filter options"
                  label="Filter options"
                  items={Object.values(taskFilters)}
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

                    if (newFilter !== 'completed' && sortBy === 'completion') {
                      form.change('sortBy', 'creation');
                    }

                    input.onChange(newFilter);
                    form.submit();
                    dropdownRef.current?.focus();
                  }}
                  selectedItem={taskFilters[input.value]}
                  onBlur={input.onBlur}
                  onFocus={input.onFocus}
                  size="md"
                />
              )}
            </Field>
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
                  menuOptionsClass={MENU_OPTIONS_STYLES_CLASSNAME}
                >
                  {sortOptionsOrder.map((id) => (
                    <OverflowMenuItem
                      key={id}
                      itemText={
                        <SortItemContainer>
                          <Checkmark
                            aria-label=""
                            size={20}
                            style={{
                              visibility:
                                input.value === id ? undefined : 'hidden',
                            }}
                          />
                          <MenuItemWrapper>
                            {SORTING_OPTIONS[id]}
                          </MenuItemWrapper>
                        </SortItemContainer>
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
          </FormElement>
        )}
      </Form>
    </Container>
  );
};

export {Filters};
