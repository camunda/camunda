/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useRef, useEffect} from 'react';

import './EntityName.scss';

export default function EntityName({children}) {
  const [showTooltip, setShowTooltip] = useState(false);
  const nameRef = useRef(null);

  useEffect(() => {
    const node = nameRef.current;
    setShowTooltip(node?.scrollWidth > node?.clientWidth);
  }, [setShowTooltip, nameRef, children]);

  return (
    <div className="EntityName">
      <h1 className="name" ref={nameRef}>
        {children}
      </h1>
      {showTooltip && (
        <div className="Tooltip dark">
          <div className="Tooltip__text-bottom">{children}</div>
        </div>
      )}
    </div>
  );
}
