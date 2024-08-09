/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';

import icons from './icons';

import './Icon.scss';

type IconProps = {
  type?: string;
  size?: number | string;
  renderedIn?: keyof JSX.IntrinsicElements;
  children?: ReactNode;
  className?: string;
};

export default function Icon(props: IconProps): JSX.Element {
  const {type, size, children, renderedIn: Wrapper, ...filteredProps} = props;

  if (Wrapper) {
    return <Wrapper {...filteredProps} className={`Icon Icon--${type}`} />;
  } else {
    const SVG = type ? icons[type] : null;

    const style = size
      ? {
          minWidth: size,
          minHeight: size,
          maxWidth: size,
          maxHeight: size,
        }
      : {};

    return (
      <span {...filteredProps} className={classnames('Icon', 'IconSvg', filteredProps.className)}>
        {SVG ? <SVG style={style} /> : children}
      </span>
    );
  }
}
