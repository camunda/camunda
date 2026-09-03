"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.timer = void 0;
function timer(func) {
    var start = new Date().getTime();
    var val = func();
    var end = new Date().getTime();
    var total = end - start;
    return { time: total, value: val };
}
exports.timer = timer;
//# sourceMappingURL=timer.js.map