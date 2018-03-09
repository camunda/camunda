import {shim as objectValuesShim} from 'object.values';
import {shim as arrayIncludesShim} from 'array-includes';
import {shim as arrayFindShim} from 'array.prototype.find';
import 'string.prototype.includes';
import 'element-closest';

if (!Object.values) {
  objectValuesShim();
}

if (typeof Number.EPSILON === 'undefined') {
  Number.EPSILON = Math.pow(2, -52);
}

arrayIncludesShim();
arrayFindShim();
