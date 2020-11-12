/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';

import './Tooltip.scss';

export default function Tooltip({
  children,
  content,
  position = 'top',
  align = 'center',
  theme = 'light',
  delay = 800,
  overflowOnly = false,
}) {
  const [timeout, setTimeout] = useState();
  const [hovering, setHovering] = useState(false);
  const [style, setStyle] = useState();

  if (!content) {
    return children;
  }

  function calculatePosition(element) {
    const box = element.getBoundingClientRect();

    let left = box.x + box.width / 2;
    if (align === 'left') {
      left = box.x;
    } else if (align === 'right') {
      left = box.x + box.width;
    }
    setStyle({left: left + 'px', top: box[position] + 'px'});
  }

  return (
    <>
      {React.Children.map(children, (child) =>
        React.cloneElement(child, {
          onMouseEnter: (evt) => {
            const {currentTarget} = evt;
            if (!overflowOnly || currentTarget.scrollWidth > currentTarget.clientWidth) {
              calculatePosition(currentTarget);
              setTimeout(window.setTimeout(() => setHovering(true), delay));
            }
            child.props.onMouseEnter && child.props.onMouseEnter(evt);
          },
          onMouseLeave: (evt) => {
            setHovering(false);
            window.clearTimeout(timeout);
            child.props.onMouseLeave && child.props.onMouseLeave(evt);
          },
        })
      )}
      {hovering &&
        ReactDOM.createPortal(
          <div className={classnames('Tooltip', position, theme, align)} style={style}>
            {content}
          </div>,
          document.body
        )}
    </>
  );
}
