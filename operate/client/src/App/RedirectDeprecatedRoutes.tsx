/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useLocation, matchPath, useNavigate} from 'react-router-dom';
import {
  migrateUrlParams,
  PROCESS_INSTANCE_PARAM_MIGRATION,
} from 'modules/utils/filter/migrateUrlParams';

const DEPRECATED_ROUTES = ['/instances', '/instances/:id'] as const;

const PROCESS_ROUTES = ['/processes', '/processes/:id'] as const;

const RedirectDeprecatedRoutes: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    // Migrate deprecated /instances paths to /processes
    if (
      DEPRECATED_ROUTES.some(
        (route) => matchPath(route, location.pathname) !== null,
      )
    ) {
      navigate(
        {
          ...location,
          pathname: location.pathname.replace(/instances/, 'processes'),
        },
        {
          replace: true,
        },
      );
      return;
    }

    // Migrate deprecated query param names on /processes routes
    if (
      PROCESS_ROUTES.some(
        (route) => matchPath(route, location.pathname) !== null,
      )
    ) {
      const migrated = migrateUrlParams(
        new URLSearchParams(location.search),
        PROCESS_INSTANCE_PARAM_MIGRATION,
      );
      if (migrated !== null) {
        navigate(
          {
            ...location,
            search: migrated.toString(),
          },
          {replace: true},
        );
        return;
      }
    }
  }, [location, navigate]);

  return null;
};

export {RedirectDeprecatedRoutes};
