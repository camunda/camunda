/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@camunda/design-system";
import { useAppData } from "../../lib/appData";
import { useGlobalRange } from "../../lib/rangeContext";
import { KpiTab } from "./KpiTab";
import { PerformanceTab } from "./PerformanceTab";
import { QualityTab } from "./QualityTab";

/**
 * Processes: KPI / Performance / Quality tabs, whose tiles adapt to the global perspective
 * (processes, or the selected object type) -- the existing Dashboards page, relocated under the new
 * IA's name (see the design sketch's build plan, P1: "keep its perspective/tab machinery working
 * as-is under the new name"; P2 replaces this with the real one-page-per-process view). The time
 * range is now the one shared header control (see lib/rangeContext.tsx) rather than a page-local
 * picker.
 */
export function DashboardsPage() {
  const { perspective } = useAppData();
  const { range } = useGlobalRange();

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Processes</h1>

      <Tabs defaultValue="kpi">
        <TabsList>
          <TabsTrigger value="kpi">KPI</TabsTrigger>
          <TabsTrigger value="performance">Performance</TabsTrigger>
          <TabsTrigger value="quality">Quality</TabsTrigger>
        </TabsList>
        <TabsContent value="kpi" className="pt-4">
          <KpiTab perspective={perspective} range={range} />
        </TabsContent>
        <TabsContent value="performance" className="pt-4">
          <PerformanceTab perspective={perspective} range={range} />
        </TabsContent>
        <TabsContent value="quality" className="pt-4">
          <QualityTab perspective={perspective} range={range} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
