/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Editor from "@monaco-editor/react";

const options: React.ComponentProps<typeof Editor>["options"] = {
  minimap: {
    enabled: false,
  },
  fontSize: 13,
  lineHeight: 20,
  fontFamily:
    '"IBM Plex Mono", "Droid Sans Mono", "monospace", monospace, "Droid Sans Fallback"',
  formatOnPaste: true,
  formatOnType: true,
  tabSize: 2,
  wordWrap: "on",
  scrollBeyondLastLine: false,
};

export { options };
