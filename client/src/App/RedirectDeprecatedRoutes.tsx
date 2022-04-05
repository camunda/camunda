/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {useLocation, matchPath, useNavigate} from 'react-router-dom';

const DEPRECATED_ROUTES = ['/instances', '/instances/:id'] as const;

const RedirectDeprecatedRoutes: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    if (location.hash !== '') {
      navigate(
        {
          ...location,
          hash: '',
        },
        {replace: true}
      );
    }

    if (
      DEPRECATED_ROUTES.some(
        (route) => matchPath(route, location.pathname) !== null
      )
    ) {
      navigate(
        {
          ...location,
          pathname: location.pathname.replace(/instances/, 'processes'),
        },
        {
          replace: true,
        }
      );
    }
  }, [location, navigate]);

  return null;
};

export {RedirectDeprecatedRoutes};
