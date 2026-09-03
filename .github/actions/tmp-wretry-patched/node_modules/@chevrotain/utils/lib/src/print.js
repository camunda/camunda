"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PRINT_WARNING = exports.PRINT_ERROR = void 0;
function PRINT_ERROR(msg) {
    /* istanbul ignore else - can't override global.console in node.js */
    if (console && console.error) {
        console.error("Error: ".concat(msg));
    }
}
exports.PRINT_ERROR = PRINT_ERROR;
function PRINT_WARNING(msg) {
    /* istanbul ignore else - can't override global.console in node.js*/
    if (console && console.warn) {
        // TODO: modify docs accordingly
        console.warn("Warning: ".concat(msg));
    }
}
exports.PRINT_WARNING = PRINT_WARNING;
//# sourceMappingURL=print.js.map