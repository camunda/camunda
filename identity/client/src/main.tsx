/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./index.scss";
import "@camunda/design-system/styles.css";
import {
  C4Provider,
  TooltipProvider,
  SidebarProvider,
} from "@camunda/design-system";
import { IS_NEW_DESIGN_SYSTEM_ENABLED } from "./feature-flags";
import "./c4-ui.css";

const app = IS_NEW_DESIGN_SYSTEM_ENABLED ? (
  <C4Provider>
    <TooltipProvider>
      <SidebarProvider>
        <App />
      </SidebarProvider>
    </TooltipProvider>
  </C4Provider>
) : (
  <App />
);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>{app}</React.StrictMode>,
);
