/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import './Icon.scss';
import icons from './icons';

export default function Icon(props) {
  const filteredProps = {...props};
  const type = props.type;
  const Tag = props.renderedIn;
  delete filteredProps.renderedIn;
  delete filteredProps.type;

  if (props.renderedIn) {
    return <Tag {...filteredProps} className={'Icon Icon--' + type} />;
  } else {
    const SVG = icons[type];

    const style = props.size
      ? {
          minWidth: props.size,
          minHeight: props.size,
          maxWidth: props.size,
          maxHeight: props.size,
        }
      : {};

    return (
      <span {...filteredProps} className={classnames('Icon', 'IconSvg', filteredProps.className)}>
        {SVG ? <SVG style={style} /> : props.children}
      </span>
    );
  }
}
