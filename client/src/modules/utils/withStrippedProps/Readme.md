# Purpose

When extending other React components, styled-components passes all the props to
the DOM element returned by the extended component.
By wrapping the component with withStrippedProps HOC, you specify the props you don't
want to pe passed down to the DOM element.

## How to use

Import the utility in your styled.js file

```
import withStrippedProps from 'modules/utils/withStrippedProps';
```

and wrap the component providing the props you want to remove:

```
export const Metric = themed(styled(withStrippedProps(['metricColor'])(Link))`
  opacity: ${({metricColor}) =>
    metricColor === 'themed' &&
    themeStyle({
      dark: 0.9,
      light: 1
    })};
  `;
```

This will assure that metricColor won't end up in the final markup.
