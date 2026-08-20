/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import useDebounce from "react-debounced";
import { Loading } from "@carbon/react";

type LateLoadingProps = {
  timeout?: number;
};

const LateLoading: FC<LateLoadingProps> = ({ timeout = 300 }) => {
  const debounce = useDebounce(timeout);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    debounce(() => setVisible(true));
  }, [debounce]);

  if (visible) {
    return <Loading />;
  }

  return null;
};

export default LateLoading;
