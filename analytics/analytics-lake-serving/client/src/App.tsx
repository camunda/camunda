/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Navigate, Route, Routes } from "react-router";
import { AppDataProvider } from "./lib/appData";
import { DashboardsPage } from "./components/dashboards/DashboardsPage";
import { DataPage } from "./components/data/DataPage";
import { ExplainPage } from "./components/explain/ExplainPage";
import { Shell } from "./components/layout/Shell";
import { ObjectDetailPage } from "./components/objects/ObjectDetailPage";
import { ObjectsIndexPage } from "./components/objects/ObjectsIndexPage";
import { ObjectsListPage } from "./components/objects/ObjectsListPage";

/**
 * App shell + routing: Dashboards / Explain / Objects / Data, with a global perspective switcher
 * (see lib/appData.tsx) shared across every page.
 */
export default function App() {
  return (
    <AppDataProvider>
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<Navigate to="/dashboards" replace />} />
          <Route path="dashboards" element={<DashboardsPage />} />
          <Route path="explain" element={<ExplainPage />} />
          <Route path="objects" element={<ObjectsIndexPage />} />
          <Route path="objects/:type" element={<ObjectsListPage />} />
          <Route path="objects/:type/:id" element={<ObjectDetailPage />} />
          <Route path="data" element={<DataPage />} />
          <Route path="*" element={<Navigate to="/dashboards" replace />} />
        </Route>
      </Routes>
    </AppDataProvider>
  );
}
