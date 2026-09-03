"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defineNameProp = void 0;
var NAME = "name";
function defineNameProp(obj, nameValue) {
    Object.defineProperty(obj, NAME, {
        enumerable: false,
        configurable: true,
        writable: false,
        value: nameValue
    });
}
exports.defineNameProp = defineNameProp;
//# sourceMappingURL=lang_extensions.js.map