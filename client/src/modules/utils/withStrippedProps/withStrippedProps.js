import React from 'react';

const withStrippedProps = propsToRemove => Component => {
  function strippedComponent(props) {
    const strippedProps = Object.entries(props)
      .filter(([key]) => !propsToRemove.includes(key))
      .reduce((stripped, [key, value]) => ({...stripped, [key]: value}), {});

    return <Component {...strippedProps} />;
  }

  strippedComponent.displayName = `withStrippedProps(${Component.displayName ||
    Component.name ||
    'Component'})`;

  strippedComponent.WrappedComponent = Component;

  return strippedComponent;
};

export default withStrippedProps;
