/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {EXPAND_STATE} from 'modules/constants';
import * as Styled from './styled';
import {useParams} from 'react-router-dom';

const IncidentsBanner = ({count, onClick, isOpen, expandState}) => {
  const {id} = useParams();
  const isOnlyOne = count === 1;
  const errorMessage = `There ${isOnlyOne ? 'is' : 'are'} ${pluralSuffix(
    count,
    'Incident'
  )} in Instance ${id}. `;
  const title = `View ${pluralSuffix(count, 'Incident')} in Instance ${id}. `;

  return (
    expandState !== EXPAND_STATE.COLLAPSED && (
      <Styled.IncidentsBanner
        onClick={onClick}
        title={title}
        isExpanded={isOpen}
        iconButtonTheme="incidentsBanner"
      >
        {errorMessage}
      </Styled.IncidentsBanner>
    )
  );
};

IncidentsBanner.propTypes = {
  count: PropTypes.number.isRequired,
  onClick: PropTypes.func,
  isOpen: PropTypes.bool,
  expandState: PropTypes.string,
};

export {IncidentsBanner};
