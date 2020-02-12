/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {createContext, useState} from 'react';
import PropTypes from 'prop-types';

const InstanceSelectionContext = createContext({});

const {Provider} = InstanceSelectionContext;

export const useInstanceSelection = () => {
  const [selectedIds, setSelectedIds] = useState([]);

  const addToSelection = id => {
    setSelectedIds([...selectedIds, id]);
  };

  const removeFromSelection = id => {
    const ids = [...selectedIds];
    const index = ids.indexOf(id);
    ids.splice(index, 1);
    setSelectedIds(ids);
  };

  const isIdSelected = id => selectedIds.indexOf(id) >= 0;

  const handleSelect = id => () => {
    isIdSelected(id) ? removeFromSelection(id) : addToSelection(id);
  };

  return {isIdSelected, handleSelect};
};

const InstanceSelectionProvider = ({children}) => {
  return <Provider value={useInstanceSelection()}>{children}</Provider>;
};

InstanceSelectionProvider.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

export {InstanceSelectionProvider, InstanceSelectionContext};
