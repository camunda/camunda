/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Route, Routes } from "react-router-dom";
import {
  useGlobalRoutes,
  RouteEntry,
} from "src/components/global/useGlobalRoutes";
import Redirect from "src/components/router/Redirect";
import { Paths } from "src/components/global/routePaths";

const GlobalRoutes: FC = () => {
  const routes = useGlobalRoutes();
  const firstRoute = routes[0];
  const indexRoute =
    "subElements" in firstRoute
      ? firstRoute.subElements[0].key
      : firstRoute.key;

  const flatRoutes = routes.flatMap((route): RouteEntry[] =>
    "subElements" in route ? route.subElements : [route],
  );

  return (
    <Routes>
      <Route index element={<Redirect to={indexRoute} />} />
      {/* Redirects for legacy URLs */}
      <Route
        path="/global-task-listeners/*"
        element={<Redirect to={Paths.globalTaskListeners()} />}
      />
      <Route
        path="/global-execution-listeners/*"
        element={<Redirect to={Paths.globalExecutionListeners()} />}
      />
      <Route
        path={Paths.listeners()}
        element={<Redirect to={Paths.globalTaskListeners()} />}
      />
      {flatRoutes.map(({ path, element }) => (
        <Route key={path} path={path} element={element} />
      ))}
      <Route path="*" element={<Redirect to="/" />} />
    </Routes>
  );
};

export default GlobalRoutes;
