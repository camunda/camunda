/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/flat#Polyfill
/*eslint-disable no-extend-native */
if (!Array.prototype.flat) {
  Array.prototype.flat = function () {
    var depth = arguments[0];
    depth = depth === undefined ? 1 : Math.floor(depth);
    if (depth < 1) {
      return Array.prototype.slice.call(this);
    }
    return (function flat(arr, depth) {
      var len = arr.length >>> 0;
      var flattened = [];
      var i = 0;
      while (i < len) {
        if (i in arr) {
          var el = arr[i];
          if (Array.isArray(el) && depth > 0) {
            flattened = flattened.concat(flat(el, depth - 1));
          } else {
            flattened.push(el);
          }
        }
        i++;
      }
      return flattened;
    })(this, depth);
  };
}
