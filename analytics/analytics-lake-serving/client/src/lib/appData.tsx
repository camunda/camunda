/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, type EntityDescriptor } from "./api";

export const PROCESSES_PERSPECTIVE = "processes";

interface AppData {
  /** "processes", or one of the object types from GET /api/objects/types. */
  perspective: string;
  setPerspective: (p: string) => void;
  /** Discovered object types. Empty when the endpoint failed or hasn't resolved yet -- callers
   * hide object perspectives entirely in that case, per the spec. */
  objectTypes: string[];
  objectTypesLoaded: boolean;
  closedSupported: boolean;
  /** GET /api/registry entities, keyed by name for O(1) lookups from forms/tiles. */
  entities: EntityDescriptor[];
  entitiesLoaded: boolean;
}

const AppDataContext = createContext<AppData | null>(null);

export function AppDataProvider({ children }: { children: ReactNode }) {
  const [perspective, setPerspective] = useState<string>(PROCESSES_PERSPECTIVE);
  const [objectTypes, setObjectTypes] = useState<string[]>([]);
  const [objectTypesLoaded, setObjectTypesLoaded] = useState(false);
  const [closedSupported, setClosedSupported] = useState(false);
  const [entities, setEntities] = useState<EntityDescriptor[]>([]);
  const [entitiesLoaded, setEntitiesLoaded] = useState(false);

  useEffect(() => {
    api.objects.types().then((result) => {
      if (result.ok) {
        setObjectTypes(result.data.types);
        setClosedSupported(result.data.closedSupported);
      }
      // On failure, objectTypes stays [] -- the perspective switcher hides object perspectives.
      setObjectTypesLoaded(true);
    });
    api.registry().then((result) => {
      if (result.ok) {
        setEntities(result.data.entities);
      }
      setEntitiesLoaded(true);
    });
  }, []);

  const value = useMemo<AppData>(
    () => ({
      perspective,
      setPerspective,
      objectTypes,
      objectTypesLoaded,
      closedSupported,
      entities,
      entitiesLoaded,
    }),
    [perspective, objectTypes, objectTypesLoaded, closedSupported, entities, entitiesLoaded],
  );

  return <AppDataContext.Provider value={value}>{children}</AppDataContext.Provider>;
}

export function useAppData(): AppData {
  const ctx = useContext(AppDataContext);
  if (!ctx) {
    throw new Error("useAppData must be used within AppDataProvider");
  }
  return ctx;
}

/** Looks up one entity's descriptor by name (undefined if the registry hasn't loaded it, or the
 * registry call failed entirely). */
export function useEntity(name: string | undefined): EntityDescriptor | undefined {
  const { entities } = useAppData();
  return useMemo(() => entities.find((e) => e.name === name), [entities, name]);
}
