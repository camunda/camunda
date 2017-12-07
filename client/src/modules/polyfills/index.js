// import './array-includes';
// import './string-includes';


import {shim as objectValuesShim} from 'object.values';
import {shim as arrayIncludesShim} from 'array-includes';
import 'string.prototype.includes';
import 'element-closest';

if (!Object.values) {
  objectValuesShim();
}

arrayIncludesShim();
