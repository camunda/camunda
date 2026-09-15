/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from "react";
import ReactDOM from "react-dom/client";
import { IS_NEW_DESIGN_SYSTEM_ENABLED } from "./feature-flags";

const App = IS_NEW_DESIGN_SYSTEM_ENABLED
  ? (await import("./AppV2")).default
  : (await import("./App")).default;

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
