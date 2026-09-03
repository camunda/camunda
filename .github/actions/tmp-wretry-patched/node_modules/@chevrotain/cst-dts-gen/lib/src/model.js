"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        if (typeof b !== "function" && b !== null)
            throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildModel = void 0;
var gast_1 = require("@chevrotain/gast");
var map_1 = __importDefault(require("lodash/map"));
var flatten_1 = __importDefault(require("lodash/flatten"));
var values_1 = __importDefault(require("lodash/values"));
var some_1 = __importDefault(require("lodash/some"));
var groupBy_1 = __importDefault(require("lodash/groupBy"));
var assign_1 = __importDefault(require("lodash/assign"));
function buildModel(productions) {
    var generator = new CstNodeDefinitionGenerator();
    var allRules = (0, values_1.default)(productions);
    return (0, map_1.default)(allRules, function (rule) { return generator.visitRule(rule); });
}
exports.buildModel = buildModel;
var CstNodeDefinitionGenerator = /** @class */ (function (_super) {
    __extends(CstNodeDefinitionGenerator, _super);
    function CstNodeDefinitionGenerator() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CstNodeDefinitionGenerator.prototype.visitRule = function (node) {
        var rawElements = this.visitEach(node.definition);
        var grouped = (0, groupBy_1.default)(rawElements, function (el) { return el.propertyName; });
        var properties = (0, map_1.default)(grouped, function (group, propertyName) {
            var allNullable = !(0, some_1.default)(group, function (el) { return !el.canBeNull; });
            // In an alternation with a label a property name can have
            // multiple types.
            var propertyType = group[0].type;
            if (group.length > 1) {
                propertyType = (0, map_1.default)(group, function (g) { return g.type; });
            }
            return {
                name: propertyName,
                type: propertyType,
                optional: allNullable
            };
        });
        return {
            name: node.name,
            properties: properties
        };
    };
    CstNodeDefinitionGenerator.prototype.visitAlternative = function (node) {
        return this.visitEachAndOverrideWith(node.definition, { canBeNull: true });
    };
    CstNodeDefinitionGenerator.prototype.visitOption = function (node) {
        return this.visitEachAndOverrideWith(node.definition, { canBeNull: true });
    };
    CstNodeDefinitionGenerator.prototype.visitRepetition = function (node) {
        return this.visitEachAndOverrideWith(node.definition, { canBeNull: true });
    };
    CstNodeDefinitionGenerator.prototype.visitRepetitionMandatory = function (node) {
        return this.visitEach(node.definition);
    };
    CstNodeDefinitionGenerator.prototype.visitRepetitionMandatoryWithSeparator = function (node) {
        return this.visitEach(node.definition).concat({
            propertyName: node.separator.name,
            canBeNull: true,
            type: getType(node.separator)
        });
    };
    CstNodeDefinitionGenerator.prototype.visitRepetitionWithSeparator = function (node) {
        return this.visitEachAndOverrideWith(node.definition, {
            canBeNull: true
        }).concat({
            propertyName: node.separator.name,
            canBeNull: true,
            type: getType(node.separator)
        });
    };
    CstNodeDefinitionGenerator.prototype.visitAlternation = function (node) {
        return this.visitEachAndOverrideWith(node.definition, { canBeNull: true });
    };
    CstNodeDefinitionGenerator.prototype.visitTerminal = function (node) {
        return [
            {
                propertyName: node.label || node.terminalType.name,
                canBeNull: false,
                type: getType(node)
            }
        ];
    };
    CstNodeDefinitionGenerator.prototype.visitNonTerminal = function (node) {
        return [
            {
                propertyName: node.label || node.nonTerminalName,
                canBeNull: false,
                type: getType(node)
            }
        ];
    };
    CstNodeDefinitionGenerator.prototype.visitEachAndOverrideWith = function (definition, override) {
        return (0, map_1.default)(this.visitEach(definition), function (definition) { return (0, assign_1.default)({}, definition, override); });
    };
    CstNodeDefinitionGenerator.prototype.visitEach = function (definition) {
        var _this = this;
        return (0, flatten_1.default)((0, map_1.default)(definition, function (definition) { return _this.visit(definition); }));
    };
    return CstNodeDefinitionGenerator;
}(gast_1.GAstVisitor));
function getType(production) {
    if (production instanceof gast_1.NonTerminal) {
        return {
            kind: "rule",
            name: production.referencedRule.name
        };
    }
    return { kind: "token" };
}
//# sourceMappingURL=model.js.map