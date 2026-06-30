/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form, Field} from 'react-final-form';
import {useSearchParams, useNavigate} from 'react-router-dom';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {SearchInput} from '../SearchInput';

const SEARCH_PARAM_KEY = 'elementSearch';

type SearchFormValues = {search: string};

const SearchForm: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const submittedSearch = searchParams.get(SEARCH_PARAM_KEY) ?? '';

  const handleSearchSubmit = (values: SearchFormValues) => {
    const next = new URLSearchParams(searchParams);
    const trimmed = (values.search ?? '').trim();
    if (trimmed) {
      next.set(SEARCH_PARAM_KEY, trimmed);
    } else {
      next.delete(SEARCH_PARAM_KEY);
    }
    navigate({search: next.toString()}, {replace: true});
  };

  const handleClearSearch = () => {
    const next = new URLSearchParams(searchParams);
    next.delete(SEARCH_PARAM_KEY);
    navigate({search: next.toString()}, {replace: true});
  };

  return (
    <Form<SearchFormValues>
      onSubmit={handleSearchSubmit}
      initialValues={{search: submittedSearch}}
      keepDirtyOnReinitialize
    >
      {({form}) => (
        <>
          <AutoSubmit />
          <Field<string> name="search" parse={(v) => v ?? ''}>
            {({input}) => (
              <SearchInput
                value={input.value}
                onChange={(value) => input.onChange(value)}
                onClear={() => {
                  form.reset({search: ''});
                  handleClearSearch();
                }}
              />
            )}
          </Field>
        </>
      )}
    </Form>
  );
};

export {SearchForm, SEARCH_PARAM_KEY};
