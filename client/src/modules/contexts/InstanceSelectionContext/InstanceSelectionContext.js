/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {createContext, useState, useEffect} from 'react';
import PropTypes from 'prop-types';

const InstanceSelectionContext = createContext({});
const {Provider} = InstanceSelectionContext;

const MODES = {
  INCLUDE: 'INCLUDE',
  EXCLUDE: 'EXCLUDE',
  ALL: 'ALL'
};

export const useInstanceSelection = totalCount => {
  const [ids, setIds] = useState([]);
  const [isAllChecked, setAllChecked] = useState(false);
  const [mode, setMode] = useState(MODES.INCLUDE);

  useEffect(() => {
    if (
      (mode === MODES.EXCLUDE && ids.length === 0) ||
      (mode === MODES.INCLUDE && ids.length === totalCount && totalCount !== 0)
    ) {
      setMode(MODES.ALL);
      setAllChecked(true);
      setIds([]);
    }
  }, [ids, mode, totalCount]);

  const addToIds = id => {
    setIds([...ids, id]);
  };

  const removeFromIds = id => {
    setIds(prevIds => prevIds.filter(prevId => prevId !== id));
  };

  const isInstanceChecked = id => {
    switch (mode) {
      case MODES.INCLUDE:
        return ids.indexOf(id) >= 0;
      case MODES.EXCLUDE:
        return ids.indexOf(id) < 0;
      default:
        return mode === MODES.ALL;
    }
  };

  const handleCheckAll = () => {
    if (mode === MODES.ALL) {
      setMode(MODES.INCLUDE);
      setAllChecked(false);
    } else {
      setMode(MODES.ALL);
      setAllChecked(true);
      setIds([]);
    }
  };

  const handleCheckInstance = id => () => {
    if (mode === MODES.ALL) {
      setMode(MODES.EXCLUDE);
      setAllChecked(false);
    }

    if (ids.indexOf(id) >= 0) {
      removeFromIds(id);
    } else {
      addToIds(id);
    }
  };

  const getSelectedCount = totalCount => {
    switch (mode) {
      case MODES.INCLUDE:
        return ids.length;
      case MODES.EXCLUDE:
        return totalCount - ids.length;
      default:
        return totalCount;
    }
  };

  return {
    isAllChecked,
    handleCheckAll,
    isInstanceChecked,
    handleCheckInstance,
    getSelectedCount
  };
};

const InstanceSelectionProvider = ({children, totalCount}) => {
  return (
    <Provider value={useInstanceSelection(totalCount)}>{children}</Provider>
  );
};

InstanceSelectionProvider.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  totalCount: PropTypes.number
};

export {InstanceSelectionProvider, InstanceSelectionContext};
