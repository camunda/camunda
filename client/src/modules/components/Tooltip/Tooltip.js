/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useRef, useEffect} from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';

import {getNonOverflowingValues} from './service';

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
  const [hovering, setHovering] = useState(false);
  const [style, setStyle] = useState();
  const [tooltipAlign, setTooltipAlign] = useState(align);
  const [tooltipPosition, setTooltipPosition] = useState(position);

  const hoverElement = useRef();
  const tooltip = useRef();
  const timeout = useRef();

  useEffect(() => {
    if (!tooltip.current || !hoverElement.current) {
      return;
    }

    const {width, left, top, newAlign, newPosition} = getNonOverflowingValues(
      tooltip.current,
      hoverElement.current,
      align,
      position
    );

    setTooltipPosition(newPosition);
    setTooltipAlign(newAlign);
    setStyle({
      width,
      left: left + 'px',
      top: top + 'px',
    });
  }, [align, hovering, position]);

  if (!content) {
    return children;
  }

  let useLightTheme = theme === 'light';
  if (document.body.classList.contains('dark')) {
    useLightTheme = !useLightTheme;
  }

  return (
    <>
      {React.Children.map(children, (child) =>
        React.cloneElement(child, {
          onMouseEnter: (evt) => {
            const {currentTarget} = evt;
            if (!overflowOnly || currentTarget.scrollWidth > currentTarget.clientWidth) {
              hoverElement.current = currentTarget;
              timeout.current = window.setTimeout(() => setHovering(true), delay);
            }
            child.props.onMouseEnter?.(evt);
          },
          onMouseLeave: (evt) => {
            hoverElement.current = null;
            window.clearTimeout(timeout.current);
            timeout.current = window.setTimeout(() => {
              setHovering(false);
              setStyle();
            }, delay);
            child.props.onMouseLeave?.(evt);
          },
        })
      )}
      {hovering &&
        ReactDOM.createPortal(
          <div
            className={classnames(
              'Tooltip',
              tooltipPosition,
              tooltipAlign,
              useLightTheme ? 'light' : 'dark'
            )}
            style={style}
            ref={tooltip}
            onMouseEnter={() => window.clearTimeout(timeout.current)}
            onMouseLeave={() => setHovering(false)}
            onClick={(e) => e.stopPropagation()}
          >
            {content}
          </div>,
          document.fullscreenElement || document.body
        )}
    </>
  );
}
