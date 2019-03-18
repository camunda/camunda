/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

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

  function handleToggle() {
    setIsOpen(!isOpen);
  }

  function handleSort(key) {
    let newSorting = {sortBy: key, sortOrder: SORT_ORDER.DESC};

    if (sorting.sortBy === key && sorting.sortOrder === SORT_ORDER.DESC) {
      newSorting.sortOrder = SORT_ORDER.ASC;
    }

    setSorting(newSorting);
  }

  const sortedIncidents = sortData(
    incidents,
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
            errorTypes={props.errorTypes}
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
