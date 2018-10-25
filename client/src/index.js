import 'react-app-polyfill/ie11';

import React from 'react';
import ReactDOM from 'react-dom';

import './style.scss';
import 'polyfills';

import App from './App';

ReactDOM.render(<App />, document.getElementById('root'));
