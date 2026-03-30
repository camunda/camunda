/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {type JSONEditorComponent, MonacoContext} from './EditorContext';

const EditorProvider: React.FC<React.PropsWithChildren> = ({children}) => {
  const [EditorComponent, setEditorComponent] =
    useState<JSONEditorComponent | null>(null);

  useEffect(() => {
    async function load() {
      const [{loadMonaco}, mod] = await Promise.all([
        import('modules/loadMonaco'),
        import('modules/components/JSONEditor'),
      ]);

      loadMonaco();
      setEditorComponent(() => mod.JSONEditor);
    }

    load();
  }, []);

  return (
    <MonacoContext.Provider value={EditorComponent}>
      {children}
    </MonacoContext.Provider>
  );
};

export {EditorProvider};
