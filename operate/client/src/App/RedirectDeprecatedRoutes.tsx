/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useLocation, matchPath, useNavigate} from 'react-router-dom';

const DEPRECATED_ROUTES = ['/instances', '/instances/:id'] as const;

const RedirectDeprecatedRoutes: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    if (
      DEPRECATED_ROUTES.some(
        (route) => matchPath(route, location.pathname) !== null,
      )
    ) {
      return navigate(
        {
          ...location,
          pathname: location.pathname.replace(/instances/, 'processes'),
        },
        {
          replace: true,
        },
      );
    }
  }, [location, navigate]);

  return null;
};

export {RedirectDeprecatedRoutes};
