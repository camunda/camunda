"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ContentAssist = void 0;
var interpreter_1 = require("../../grammar/interpreter");
var first_1 = __importDefault(require("lodash/first"));
var isUndefined_1 = __importDefault(require("lodash/isUndefined"));
var ContentAssist = /** @class */ (function () {
    function ContentAssist() {
    }
    ContentAssist.prototype.initContentAssist = function () { };
    ContentAssist.prototype.computeContentAssist = function (startRuleName, precedingInput) {
        var startRuleGast = this.gastProductionsCache[startRuleName];
        if ((0, isUndefined_1.default)(startRuleGast)) {
            throw Error("Rule ->".concat(startRuleName, "<- does not exist in this grammar."));
        }
        return (0, interpreter_1.nextPossibleTokensAfter)([startRuleGast], precedingInput, this.tokenMatcher, this.maxLookahead);
    };
    // TODO: should this be a member method or a utility? it does not have any state or usage of 'this'...
    // TODO: should this be more explicitly part of the public API?
    ContentAssist.prototype.getNextPossibleTokenTypes = function (grammarPath) {
        var topRuleName = (0, first_1.default)(grammarPath.ruleStack);
        var gastProductions = this.getGAstProductions();
        var topProduction = gastProductions[topRuleName];
        var nextPossibleTokenTypes = new interpreter_1.NextAfterTokenWalker(topProduction, grammarPath).startWalking();
        return nextPossibleTokenTypes;
    };
    return ContentAssist;
}());
exports.ContentAssist = ContentAssist;
//# sourceMappingURL=context_assist.js.map