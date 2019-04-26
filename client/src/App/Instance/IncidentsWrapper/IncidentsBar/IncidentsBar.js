/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function IncidentsBar({id, count, onClick, isArrowFlipped}) {
  const isOnlyOne = count === 1;
  const errorMessage = `There ${isOnlyOne ? 'is' : 'are'} ${count} Incident${
    isOnlyOne ? '' : 's'
  } in Instance ${id}. `;
  const title = `View ${count} Incident${
    isOnlyOne ? '' : 's'
  } in Instance ${id}. `;

  return (
    <Styled.IncidentsBar onClick={onClick} title={title}>
      <Styled.Transition in={isArrowFlipped} timeout={400}>
        <Styled.Arrow />
      </Styled.Transition>

      {errorMessage}
    </Styled.IncidentsBar>
  );
}

IncidentsBar.propTypes = {
  id: PropTypes.string.isRequired,
  count: PropTypes.number.isRequired,
  onClick: PropTypes.func,
  isArrowFlipped: PropTypes.bool
};
