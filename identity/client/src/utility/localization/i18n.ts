/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18next from "i18next";
import en from "./en";

const i18n = i18next.createInstance(
  {
    fallbackLng: "en",
    ns: Object.keys(en),
    defaultNS: "components",
    fallbackNS: "components",
    debug: true,
    interpolation: {
      escapeValue: false,
    },
    nsSeparator: false,
    resources: {
      en,
    },
  },
  () => {},
);

export default i18n;
