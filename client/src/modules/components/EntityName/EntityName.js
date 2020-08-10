/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useRef, useEffect} from 'react';
import {Link} from 'react-router-dom';

import {Popover} from 'components';

import './EntityName.scss';

export default function EntityName({children, details, linkTo}) {
  const [showTooltip, setShowTooltip] = useState(false);
  const [hovering, setHovering] = useState(false);
  const nameRef = useRef(null);

  useEffect(() => {
    const node = nameRef.current;
    setShowTooltip(node?.scrollWidth > node?.clientWidth);
  }, [setShowTooltip, nameRef, children]);

  const titleProps = {
    className: 'name',
    ref: nameRef,
    onMouseEnter: () => setHovering(true),
    onMouseLeave: () => setHovering(false),
  };

  return (
    <div className="EntityName">
      <div className="name-container">
        {linkTo ? (
          <Link to={linkTo} {...titleProps}>
            {children}
          </Link>
        ) : (
          <h1 {...titleProps}>{children}</h1>
        )}
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
