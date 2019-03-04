/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import PropTypes from 'prop-types';
import IncidentsBar from './../IncidentsBar';
import IncidentsOverlay from './../IncidentsOverlay';
import IncidentsTable from './../IncidentsTable';

function IncidentsWrapper(props) {
  const {incidents, incidentsCount, instanceId} = props;
  const [isOpen, setIsOpen] = useState(false);
  function handleToggle() {
    setIsOpen(!isOpen);
  }

  return (
    <>
      <IncidentsBar
        id={instanceId}
        count={incidentsCount}
        onClick={handleToggle}
        isArrowFlipped={isOpen}
      />
      {isOpen && (
        <IncidentsOverlay>
          <IncidentsTable incidents={incidents} />
        </IncidentsOverlay>
      )}
    </>
  );
}

IncidentsWrapper.defaultProps = {
  incidents: PropTypes.array,
  incidentsCount: PropTypes.number,
  instanceId: PropTypes.string
};

export default IncidentsWrapper;
