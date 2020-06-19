/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useRef, useEffect} from 'react';

import {Popover} from 'components';

import './EntityName.scss';

export default function EntityName({children, details}) {
  const [showTooltip, setShowTooltip] = useState(false);
  const [hovering, setHovering] = useState(false);
  const nameRef = useRef(null);

  useEffect(() => {
    const node = nameRef.current;
    setShowTooltip(node?.scrollWidth > node?.clientWidth);
  }, [setShowTooltip, nameRef, children]);

  return (
    <div className="EntityName">
      <div className="name-container">
        <h1
          className="name"
          ref={nameRef}
          onMouseEnter={() => setHovering(true)}
          onMouseLeave={() => setHovering(false)}
        >
          {children}
        </h1>
        {details && <Popover icon="down">{details}</Popover>}
      </div>
      {showTooltip && hovering && (
        <div className="Tooltip dark">
          <div className="Tooltip__text-bottom">{children}</div>
        </div>
      )}
    </div>
  );
}
