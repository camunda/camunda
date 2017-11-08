// import './array-includes';
// import './string-includes';


import {shim as objectValuesShim} from 'object.values';
import {shim as arrayIncludesShim} from 'array-includes';
import 'string.prototype.includes';

if (!Object.values) {
  objectValuesShim();
}

arrayIncludesShim();
