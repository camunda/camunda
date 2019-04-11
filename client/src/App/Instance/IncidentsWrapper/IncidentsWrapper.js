/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useMemo} from 'react';

import PropTypes from 'prop-types';
import IncidentsBar from './IncidentsBar';
import IncidentsOverlay from './IncidentsOverlay';
import IncidentsTable from './IncidentsTable';
import IncidentsFilter from './IncidentsFilter';
import {SORT_ORDER} from 'modules/constants';
import {sortData} from './service';

function IncidentsWrapper(props) {
  const {
    incidents,
    incidentsCount,
    instance,
    errorTypes,
    flowNodes,
    selectedFlowNodeInstanceIds,
    onIncidentOperation,
    onIncidentSelection
  } = props;

  const [isOpen, setIsOpen] = useState(false);
  const [sorting, setSorting] = useState({
    sortBy: 'errorType',
    sortOrder: SORT_ORDER.DESC
  });
  const [selectedFlowNodes, setSelectedFlowNodes] = useState([]);
  const [selectedErrorTypes, setSelectedErrorTypes] = useState([]);

  useEffect(
    () => {
      setSelectedFlowNodes(
        updateSelectedFilters(selectedFlowNodes, flowNodes, 'flowNodeId')
      );
    },
    [flowNodes.length]
  );

  useEffect(
    () => {
      setSelectedErrorTypes(
        updateSelectedFilters(selectedErrorTypes, errorTypes, 'errorType')
      );
    },
    [errorTypes.length]
  );

  function updateSelectedFilters(currentState, props, type) {
    return currentState.reduce(
      (updatedState, element) =>
        props.find(newElement => newElement[type] === element)
          ? [...updatedState, element]
          : updatedState,
      []
    );
  }

  function handleToggle() {
    setIsOpen(!isOpen);
  }

  function handleSelection(selectedFilters, updateFilterState) {
    return id => {
      let index = selectedFilters.findIndex(item => item === id);
      let list = [...selectedFilters];
      if (index === -1) {
        list.push(id);
      } else {
        list.splice(index, 1);
      }
      updateFilterState(list);
    };
  }

  function handleSort(key) {
    let newSortOrder =
      sorting.sortBy === key && sorting.sortOrder === SORT_ORDER.DESC
        ? SORT_ORDER.ASC
        : SORT_ORDER.DESC;

    setSorting({sortOrder: newSortOrder, sortBy: key});
  }

  function clearAll() {
    setSelectedErrorTypes([]);
    setSelectedFlowNodes([]);
  }

  const filteredIncidents = useMemo(() => filterIncidents(), [
    selectedErrorTypes,
    selectedFlowNodes,
    incidents
  ]);

  function filterIncidents() {
    const hasSelectedFlowNodes = Boolean(selectedFlowNodes.length);
    const hasSelectedErrorTypes = Boolean(selectedErrorTypes.length);

    if (!hasSelectedFlowNodes && !hasSelectedErrorTypes) {
      return incidents;
    }

    return incidents.filter(item => {
      if (!hasSelectedFlowNodes) {
        return selectedErrorTypes.includes(item.errorType);
      }

      if (!hasSelectedErrorTypes) {
        return selectedFlowNodes.includes(item.flowNodeId);
      }

      return (
        selectedFlowNodes.includes(item.flowNodeId) &&
        selectedErrorTypes.includes(item.errorType)
      );
    });
  }

  const sortedIncidents = sortData(
    filteredIncidents,
    sorting.sortBy,
    sorting.sortOrder
  );

  return (
    <>
      <IncidentsBar
        id={instance.id}
        count={incidentsCount}
        onClick={handleToggle}
        isArrowFlipped={isOpen}
      />
      {isOpen && (
        <IncidentsOverlay>
          <IncidentsFilter
            flowNodes={props.flowNodes}
            selectedFlowNodes={selectedFlowNodes}
            errorTypes={props.errorTypes}
            selectedErrorTypes={selectedErrorTypes}
            onFlowNodeSelect={handleSelection(
              selectedFlowNodes,
              setSelectedFlowNodes
            )}
            onErrorTypeSelect={handleSelection(
              selectedErrorTypes,
              setSelectedErrorTypes
            )}
            onClearAll={clearAll}
          />
          <IncidentsTable
            incidents={sortedIncidents}
            instanceId={instance.id}
            forceSpinner={props.forceSpinner}
            selectedFlowNodeInstanceIds={selectedFlowNodeInstanceIds}
            sorting={sorting}
            onIncidentOperation={onIncidentOperation}
            onIncidentSelection={onIncidentSelection}
            onSort={handleSort}
          />
        </IncidentsOverlay>
      )}
    </>
  );
}

IncidentsWrapper.propTypes = {
  incidents: PropTypes.array,
  incidentsCount: PropTypes.number.isRequired,
  instance: PropTypes.object.isRequired,
  onIncidentOperation: PropTypes.func.isRequired,
  forceSpinner: PropTypes.bool,
  selectedFlowNodeInstanceIds: PropTypes.array,
  onIncidentSelection: PropTypes.func.isRequired,
  flowNodes: PropTypes.array,
  errorTypes: PropTypes.array
};

export default IncidentsWrapper;
