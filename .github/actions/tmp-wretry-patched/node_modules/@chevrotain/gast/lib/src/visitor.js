"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.GAstVisitor = void 0;
var model_1 = require("./model");
var GAstVisitor = /** @class */ (function () {
    function GAstVisitor() {
    }
    GAstVisitor.prototype.visit = function (node) {
        var nodeAny = node;
        switch (nodeAny.constructor) {
            case model_1.NonTerminal:
                return this.visitNonTerminal(nodeAny);
            case model_1.Alternative:
                return this.visitAlternative(nodeAny);
            case model_1.Option:
                return this.visitOption(nodeAny);
            case model_1.RepetitionMandatory:
                return this.visitRepetitionMandatory(nodeAny);
            case model_1.RepetitionMandatoryWithSeparator:
                return this.visitRepetitionMandatoryWithSeparator(nodeAny);
            case model_1.RepetitionWithSeparator:
                return this.visitRepetitionWithSeparator(nodeAny);
            case model_1.Repetition:
                return this.visitRepetition(nodeAny);
            case model_1.Alternation:
                return this.visitAlternation(nodeAny);
            case model_1.Terminal:
                return this.visitTerminal(nodeAny);
            case model_1.Rule:
                return this.visitRule(nodeAny);
            /* istanbul ignore next */
            default:
                throw Error("non exhaustive match");
        }
    };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitNonTerminal = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitAlternative = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitOption = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitRepetition = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitRepetitionMandatory = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitRepetitionMandatoryWithSeparator = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitRepetitionWithSeparator = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitAlternation = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitTerminal = function (node) { };
    /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
    GAstVisitor.prototype.visitRule = function (node) { };
    return GAstVisitor;
}());
exports.GAstVisitor = GAstVisitor;
//# sourceMappingURL=visitor.js.map