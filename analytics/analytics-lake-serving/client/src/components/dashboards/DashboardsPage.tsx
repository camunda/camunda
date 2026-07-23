/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@camunda/design-system";
import { useAppData } from "../../lib/appData";
import { DashboardRangePicker, defaultRange } from "./DashboardRangePicker";
import { KpiTab } from "./KpiTab";
import { PerformanceTab } from "./PerformanceTab";
import { QualityTab } from "./QualityTab";

/** Dashboards: KPI / Performance / Quality tabs, whose tiles adapt to the global perspective
 * (processes, or the selected object type). One shared time range drives every tile. */
export function DashboardsPage() {
  const { perspective } = useAppData();
  const [range, setRange] = useState(defaultRange());

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Dashboards</h1>
        <DashboardRangePicker selected={range.label} onSelect={setRange} />
      </div>

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
