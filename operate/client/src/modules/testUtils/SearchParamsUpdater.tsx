/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fireEvent, screen} from 'modules/testing-library';
import {useNavigate} from 'react-router-dom';

/**
 * Used to programmatically update search params after initial render in tests.
 * Must be used together with a rendered {@linkcode SearchParamsUpdater}
 * component to work.
 */
const updateSearchParams = (params: Record<string, string>) => {
  const paramsString = Object.entries(params)
    .map(([key, value]) => `${key}=${value}`)
    .join('&');
  fireEvent.change(screen.getByTestId('new-search-params'), {
    target: {value: paramsString},
  });
};

const SearchParamsUpdater: React.FC = () => {
  const navigate = useNavigate();
  const handleChange: React.ChangeEventHandler<HTMLInputElement> = (event) => {
    navigate({search: `?${event.currentTarget.value}`}, {replace: true});
    event.currentTarget.value = '';
  };
  return <input data-testid="new-search-params" onChange={handleChange} />;
};

export {updateSearchParams, SearchParamsUpdater};
