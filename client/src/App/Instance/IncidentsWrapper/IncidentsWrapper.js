/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useMemo, useRef} from 'react';
import {isEqual} from 'lodash';

import PropTypes from 'prop-types';
import IncidentsBar from './IncidentsBar';
import IncidentsOverlay from './IncidentsOverlay';
import IncidentsTable from './IncidentsTable';
import IncidentsFilter from './IncidentsFilter';
import {SORT_ORDER} from 'modules/constants';
import {sortData} from './service';

import * as Styled from './styled';

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
  const [isInTransition, setIsInTransition] = useState(false);

  const prevErrorTypes = usePrevious(errorTypes);
  const prevFlowNodes = usePrevious(flowNodes);

  useEffect(() => {
    if (didFiltersChange(prevErrorTypes, errorTypes)) {
      setSelectedErrorTypes(updateFilters(selectedErrorTypes, errorTypes));
    }
  });

  useEffect(() => {
    if (didFiltersChange(prevFlowNodes, flowNodes)) {
      setSelectedFlowNodes(updateFilters(selectedFlowNodes, flowNodes));
    }
  });

  function usePrevious(value) {
    const ref = useRef();
    useEffect(() => {
      ref.current = value;
    });
    return ref.current;
  }

  function didFiltersChange(previous, current) {
    return previous && !isEqual([...current.keys()], [...previous.keys()]);
  }

  function updateFilters(previous, current) {
    return previous.reduce(
      (updatedFilters, element) =>
        !!current.get(element) ? [...updatedFilters, element] : updatedFilters,
      []
    );
  }

  function handleToggle() {
    !isInTransition && setIsOpen(!isOpen);
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

    const isSelected = item => {
      if (hasSelectedErrorTypes && hasSelectedFlowNodes) {
        return (
          selectedFlowNodes.includes(item.flowNodeId) &&
          selectedErrorTypes.includes(item.errorType)
        );
      }
      if (hasSelectedErrorTypes) {
        return selectedErrorTypes.includes(item.errorType);
      }

      if (hasSelectedFlowNodes) {
        return selectedFlowNodes.includes(item.flowNodeId);
      }
    };

    return [...incidents.values()].filter(item => isSelected(item));
  }

  const sortedIncidents = sortData(
    filteredIncidents,
    sorting.sortBy,
    sorting.sortOrder
  );

  const onEnter = () => {
    setIsInTransition(true);
  };

  const onEntered = () => {
    setIsInTransition(false);
  };

  const onExit = () => {
    setIsInTransition(true);
  };

  const onExited = () => {
    setIsInTransition(false);
  };

  return (
    <>
      <IncidentsBar
        id={instance.id}
        count={incidentsCount}
        onClick={handleToggle}
        isArrowFlipped={isOpen}
      />
      <Styled.Transition
        in={isOpen}
        onEnter={onEnter}
        onEntered={onEntered}
        onExit={onExit}
        onExited={onExited}
        mountOnEnter
        unmountOnExit
        timeout={400}
      >
        <IncidentsOverlay>
          <IncidentsFilter
            flowNodes={flowNodes}
            selectedFlowNodes={selectedFlowNodes}
            errorTypes={errorTypes}
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
      </Styled.Transition>
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
  flowNodes: PropTypes.object,
  errorTypes: PropTypes.object
};

export default IncidentsWrapper;
