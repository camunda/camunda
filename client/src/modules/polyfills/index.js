import {shim as objectValuesShim} from 'object.values';
import {shim as arrayIncludesShim} from 'array-includes';
import {shim as arrayFindShim} from 'array.prototype.find';
import 'string.prototype.includes';
import 'element-closest';

import './array_findIndex';
import './number_isNaN';
import './number_epsilon';
import './nodeList_forEach';
import './array_from';

if (!Object.values) {
  objectValuesShim();
}

arrayIncludesShim();
arrayFindShim();
