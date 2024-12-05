/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Route, Routes } from "react-router-dom";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import Redirect from "src/components/router/Redirect";
import { baseUrl } from "src/configuration";

const GlobalRoutes: FC = () => {
  const routes = useGlobalRoutes();
  const indexRoute = routes[0].key;
  return (
    <Routes>
      <Route index element={<Redirect to={baseUrl + indexRoute} />} />
      {routes.map(({ key, path, element }) => (
        <Route key={key} path={path} element={element} />
      ))}
      <Route path="*" element={<Redirect to={baseUrl} />} />
    </Routes>
  );
};

export default GlobalRoutes;
