"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateCstDts = void 0;
var model_1 = require("./model");
var generate_1 = require("./generate");
var defaultOptions = {
    includeVisitorInterface: true,
    visitorInterfaceName: "ICstNodeVisitor"
};
function generateCstDts(productions, options) {
    var effectiveOptions = __assign(__assign({}, defaultOptions), options);
    var model = (0, model_1.buildModel)(productions);
    return (0, generate_1.genDts)(model, effectiveOptions);
}
exports.generateCstDts = generateCstDts;
//# sourceMappingURL=api.js.map