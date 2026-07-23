/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Navigate, Route, Routes } from "react-router";
import { AppDataProvider } from "./lib/appData";
import { RangeProvider } from "./lib/rangeContext";
import { DashboardsPage } from "./components/dashboards/DashboardsPage";
import { DataPage } from "./components/data/DataPage";
import { ExplainPage } from "./components/explain/ExplainPage";
import { Shell } from "./components/layout/Shell";
import { ObjectDetailPage } from "./components/objects/ObjectDetailPage";
import { ObjectsIndexPage } from "./components/objects/ObjectsIndexPage";
import { ObjectsListPage } from "./components/objects/ObjectsListPage";
import { TodayPage } from "./components/today/TodayPage";

/**
 * App shell + routing: Today / Processes / Objects / Ask why / Data, with a global perspective
 * switcher (see lib/appData.tsx) and a global time range (see lib/rangeContext.tsx) shared across
 * every page.
 *
 * Route-path note (see the P1 lane report for the full writeup): the backend's
 * SpaForwardController only forwards /dashboards, /explain, /objects, /data to index.html -- so a
 * hard deep-link (not a client-side navigation) to any *other* path 404s before the SPA bundle even
 * loads. Today and Ask why therefore render at the existing, backend-forwarded /dashboards and
 * /explain paths (nav labels renamed; URLs unchanged), with /today and /ask kept only as
 * client-side Navigate aliases onto them. Processes is a genuinely new destination with no
 * backend-forwarded path yet (/processes) -- same class of gap, flagged for the next backend lane
 * to close by extending SpaForwardController.
 */
export default function App() {
  return (
    <AppDataProvider>
      <RangeProvider>
        <Routes>
          <Route element={<Shell />}>
            <Route index element={<Navigate to="/dashboards" replace />} />
            <Route path="dashboards" element={<TodayPage />} />
            <Route path="today" element={<Navigate to="/dashboards" replace />} />
            <Route path="processes" element={<DashboardsPage />} />
            <Route path="explain" element={<ExplainPage />} />
            <Route path="ask" element={<Navigate to="/explain" replace />} />
            <Route path="objects" element={<ObjectsIndexPage />} />
            <Route path="objects/:type" element={<ObjectsListPage />} />
            <Route path="objects/:type/:id" element={<ObjectDetailPage />} />
            <Route path="data" element={<DataPage />} />
            <Route path="*" element={<Navigate to="/dashboards" replace />} />
          </Route>
        </Routes>
      </RangeProvider>
    </AppDataProvider>
  );
}
