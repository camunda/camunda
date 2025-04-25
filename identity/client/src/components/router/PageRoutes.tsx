/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactElement } from "react";
import { Route, Routes } from "react-router-dom";
import Redirect from "src/components/router/Redirect";

type PageRoutesProps = {
  indexElement: ReactElement;
  detailElement?: ReactElement;
};

const PageRoutes: FC<PageRoutesProps> = ({ indexElement, detailElement }) => {
  return (
    <Routes>
      <Route index element={indexElement} />
      {detailElement && (
        <>
          <Route path=":id" element={detailElement} />
          <Route path=":id/:tab" element={detailElement} />
        </>
      )}
      <Route path="*" element={<Redirect to=".." />} />
    </Routes>
  );
};

export default PageRoutes;
