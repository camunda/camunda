/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Navigate, useNavigate } from "react-router";
import { Button } from "@camunda/design-system";
import { PROCESSES_PERSPECTIVE, useAppData } from "../../lib/appData";
import { EmptyTile } from "../common/EmptyTile";

/** /objects with no type in the URL: jumps straight to the current perspective's object type if
 * one is selected, otherwise offers a pick list (or an empty state if none were discovered). */
export function ObjectsIndexPage() {
  const { perspective, objectTypes, objectTypesLoaded } = useAppData();
  const navigate = useNavigate();

  if (perspective !== PROCESSES_PERSPECTIVE) {
    return <Navigate to={`/objects/${encodeURIComponent(perspective)}`} replace />;
  }

  if (!objectTypesLoaded) {
    return null;
  }

  if (objectTypes.length === 0) {
    return (
      <EmptyTile
        heading="No object types discovered"
        description="GET /api/objects/types returned none -- object perspectives aren't available yet."
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Objects</h1>
      <p className="text-sm text-neutral-foreground-muted">Pick a type to browse:</p>
      <div className="flex flex-wrap gap-2">
        {objectTypes.map((t) => (
          <Button key={t} variant="secondary" onClick={() => navigate(`/objects/${encodeURIComponent(t)}`)}>
            {t}
          </Button>
        ))}
      </div>
    </div>
  );
}
