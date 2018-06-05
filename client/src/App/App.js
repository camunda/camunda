import React from 'react';
import {ThemeProvider} from './ThemeContext';

import {Dashboard} from './Dashboard';

export default function App() {
  return (
    <ThemeProvider>
      <Dashboard />
    </ThemeProvider>
  );
}
