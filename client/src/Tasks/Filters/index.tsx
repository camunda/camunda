/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form, Field} from 'react-final-form';
import {useLocation, useNavigate} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';
import {Container} from './styled';
import {OPTIONS} from './constants';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';
import {tracking} from 'modules/tracking';
import {Dropdown} from '@carbon/react';
import {useRef} from 'react';

type FilterOption = keyof typeof OPTIONS;

function isFilterOption(filter: unknown): filter is FilterOption {
  return Object.keys(OPTIONS).includes(`${filter}`);
}

type FormValues = {
  filter: FilterOption;
};

type Props = {
  disabled: boolean;
};

const Filters: React.FC<Props> = ({disabled}) => {
  const location = useLocation();
  const navigate = useNavigate();
  const selectedFilter = getSearchParam('filter', location.search);
  const dropdownRef = useRef<null | HTMLButtonElement>(null);

  return (
    <Container title="Filters">
      <Form<FormValues>
        onSubmit={(values) => {
          const searchParams = new URLSearchParams(location.search);

          tracking.track({
            eventName: 'tasks-filtered',
            filter: values.filter,
          });

          searchParams.set('filter', values.filter);

          navigate({
            pathname: Pages.Initial(),
            search: searchParams.toString(),
          });
        }}
        initialValues={{
          filter: isFilterOption(selectedFilter)
            ? selectedFilter
            : FilterValues.AllOpen,
        }}
      >
        {({handleSubmit, form}) => (
          <form onSubmit={handleSubmit}>
            <Field<FormValues['filter']> name="filter">
              {({input}) => (
                <Dropdown<{id: FilterOption; text: string}>
                  ref={dropdownRef}
                  id={input.name}
                  titleText="Filter options"
                  label="Filter options"
                  items={Object.values(OPTIONS)}
                  itemToString={(item) => (item ? item.text : '')}
                  disabled={disabled}
                  onChange={(event) => {
                    if (typeof event.selectedItem?.id === 'string') {
                      input.onChange(event.selectedItem.id);
                      form.submit();
                      dropdownRef.current?.focus();
                    }
                  }}
                  selectedItem={OPTIONS[input.value]}
                  onBlur={input.onBlur}
                  onFocus={input.onFocus}
                  size="sm"
                />
              )}
            </Field>
          </form>
        )}
      </Form>
    </Container>
  );
};

export {Filters};
