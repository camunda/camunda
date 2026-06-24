/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
