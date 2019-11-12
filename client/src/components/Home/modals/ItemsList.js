/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {LoadingIndicator} from 'components';
import Checklist from './Checklist';

export default function ItemsList({selectedItems, allItems, onChange, formatter}) {
  if (!allItems) {
    return <LoadingIndicator />;
  }

  const updateItems = (itemId, checked) => {
    if (checked) {
      const itemToSelect = allItems.find(({id, key}) => (key || id) === itemId);
      onChange([...selectedItems, itemToSelect]);
    } else {
      onChange(selectedItems.filter(({id, key}) => (key || id) !== itemId));
    }
  };

  return (
    <Checklist
      data={formatter(allItems, selectedItems)}
      onChange={updateItems}
      selectAll={() => onChange(allItems)}
      deselectAll={() => onChange([])}
    />
  );
}
