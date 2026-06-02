/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Search} from '@carbon/react';

type Props = {
  value: string;
  onChange: (value: string) => void;
  onClear: () => void;
};

const SearchInput: React.FC<Props> = ({value, onChange, onClear}) => {
  return (
    <Search
      size="sm"
      labelText="Search element instances"
      placeholder="Element name or ID"
      data-testid="instance-history-search-input"
      value={value}
      onChange={(event) => {
        onChange(event.target.value);
      }}
      onClear={onClear}
    />
  );
};

export {SearchInput};
