import React from 'react';

import './Icon.css';
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

    return (
      <span {...filteredProps} className="Icon Icon--svg">
        {SVG ? <SVG /> : props.children}
      </span>
    );
  }
}
