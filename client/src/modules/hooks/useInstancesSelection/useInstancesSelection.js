/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';

export default function useInstancesSelection() {
  const [selectedInstanceIds, setSelectedInstanceIds] = useState([]);

  const isIdSelected = id => selectedInstanceIds.indexOf(id) >= 0;

  const addToSelection = id => {
    setSelectedInstanceIds([...selectedInstanceIds, id]);
  };

  const removeFromSelection = id => {
    const ids = [...selectedInstanceIds];
    const index = ids.indexOf(id);
    ids.splice(index, 1);
    setSelectedInstanceIds(ids);
  };

  const handleSelect = id => () => {
    isIdSelected(id) ? removeFromSelection(id) : addToSelection(id);
  };

  return {isIdSelected, handleSelect};
}
