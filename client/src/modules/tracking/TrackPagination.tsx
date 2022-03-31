/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {tracking} from 'modules/tracking';

const TrackPagination: React.FC = () => {
  const location = useLocation();

  useEffect(() => {
    tracking.trackPagination();
  }, [location.pathname]);

  return null;
};

export {TrackPagination};
