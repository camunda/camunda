/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Mirrors analytics-webapp/client/src/components/ChartCard.tsx exactly (same fixed-height plot
 * area, same header/action layout) so both apps' tiles read as one family.
 */
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@camunda/design-system";
import type { ReactNode } from "react";

interface ChartCardProps {
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
  /** Optional header control(s) (e.g. the ⌕ explain-link, a compare toggle), rendered right of the
   * title block. */
  action?: ReactNode;
}

/** A titled card that holds one chart, with a fixed-height plot area. */
export function ChartCard({ title, description, children, className, action }: ChartCardProps) {
  return (
    <Card className={className}>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <CardTitle>{title}</CardTitle>
            {description ? <CardDescription>{description}</CardDescription> : null}
          </div>
          {action ?? null}
        </div>
      </CardHeader>
      <CardContent>
        <div className="h-72 w-full">{children}</div>
      </CardContent>
    </Card>
  );
}
