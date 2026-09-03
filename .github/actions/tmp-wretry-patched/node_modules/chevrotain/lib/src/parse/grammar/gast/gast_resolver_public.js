"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateGrammar = exports.resolveGrammar = void 0;
var forEach_1 = __importDefault(require("lodash/forEach"));
var defaults_1 = __importDefault(require("lodash/defaults"));
var resolver_1 = require("../resolver");
var checks_1 = require("../checks");
var errors_public_1 = require("../../errors_public");
function resolveGrammar(options) {
    var actualOptions = (0, defaults_1.default)(options, {
        errMsgProvider: errors_public_1.defaultGrammarResolverErrorProvider
    });
    var topRulesTable = {};
    (0, forEach_1.default)(options.rules, function (rule) {
        topRulesTable[rule.name] = rule;
    });
    return (0, resolver_1.resolveGrammar)(topRulesTable, actualOptions.errMsgProvider);
}
exports.resolveGrammar = resolveGrammar;
function validateGrammar(options) {
    options = (0, defaults_1.default)(options, {
        errMsgProvider: errors_public_1.defaultGrammarValidatorErrorProvider
    });
    return (0, checks_1.validateGrammar)(options.rules, options.maxLookahead, options.tokenTypes, options.errMsgProvider, options.grammarName);
}
exports.validateGrammar = validateGrammar;
//# sourceMappingURL=gast_resolver_public.js.map