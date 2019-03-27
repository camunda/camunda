/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useMemo} from 'react';

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
    selectedIncidents,
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

  function handleToggle() {
    setIsOpen(!isOpen);
  }

  function handleFlowNodeSelect(id) {
    let index = selectedFlowNodes.findIndex(item => item === id);
    let list = [...selectedFlowNodes];
    if (index === -1) {
      list.push(id);
    } else {
      list.splice(index, 1);
    }

    setSelectedFlowNodes(list);
  }

  function handleErrorTypeSelect(id) {
    let index = selectedErrorTypes.findIndex(item => item === id);
    let list = [...selectedErrorTypes];
    if (index === -1) {
      list.push(id);
    } else {
      list.splice(index, 1);
    }

    setSelectedErrorTypes(list);
  }

  function handleSort(key) {
    let newSortOrder =
      sorting.sortBy === key && sorting.sortOrder === SORT_ORDER.DESC
        ? SORT_ORDER.ASC
        : SORT_ORDER.DESC;

    setSorting({sortOrder: newSortOrder, sortBy: key});
  }

  function handleClearAll() {
    setSelectedErrorTypes([]);
    setSelectedFlowNodes([]);
  }

  const filteredIncidents = useMemo(() => filterIncidents(), [
    selectedErrorTypes,
    selectedFlowNodes,
    incidents
  ]);

  function filterIncidents() {
    if (
      !Boolean(selectedFlowNodes.length) &&
      !Boolean(selectedErrorTypes.length)
    ) {
      return incidents;
    }

    return incidents.filter(item => {
      const hasSelectedFlowNodes = Boolean(selectedFlowNodes.length);
      const hasSelectedErrorTypes = Boolean(selectedErrorTypes.length);

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
            onFlowNodeSelect={handleFlowNodeSelect}
            onErrorTypeSelect={handleErrorTypeSelect}
            onClearAll={handleClearAll}
          />
          <IncidentsTable
            incidents={sortedIncidents}
            instanceId={instance.id}
            forceSpinner={props.forceSpinner}
            selectedIncidents={selectedIncidents}
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
  selectedIncidents: PropTypes.array,
  onIncidentSelection: PropTypes.func.isRequired,
  flowNodes: PropTypes.array,
  errorTypes: PropTypes.array
};

export default IncidentsWrapper;
