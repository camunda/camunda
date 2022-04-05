/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form, Field} from 'react-final-form';
import {useLocation, useNavigate} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';
import {Select} from 'modules/components/Select';
import {Container} from './styled';
import {OPTIONS} from './constants';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';
import {useTasks} from '../useTasks';
import {tracking} from 'modules/tracking';

interface FormValues {
  filter: string;
}

const Filters: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const {loading} = useTasks({withPolling: false});

  return (
    <Container>
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
          filter:
            getSearchParam('filter', location.search) ?? FilterValues.AllOpen,
        }}
      >
        {({handleSubmit, form}) => (
          <form onSubmit={handleSubmit}>
            <Field<FormValues['filter']> name="filter">
              {({input}) => (
                <Select
                  {...input}
                  name={input.name}
                  id={input.name}
                  onChange={(event) => {
                    input.onChange(event);
                    form.submit();
                  }}
                  disabled={loading}
                  aria-label="Filter"
                >
                  {OPTIONS.map(({value, label}) => {
                    return (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    );
                  })}
                </Select>
              )}
            </Field>
          </form>
        )}
      </Form>
    </Container>
  );
};

export {Filters};
