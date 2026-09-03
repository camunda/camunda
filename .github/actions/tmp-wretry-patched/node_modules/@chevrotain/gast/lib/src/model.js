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
exports.serializeProduction = exports.serializeGrammar = exports.Terminal = exports.Alternation = exports.RepetitionWithSeparator = exports.Repetition = exports.RepetitionMandatoryWithSeparator = exports.RepetitionMandatory = exports.Option = exports.Alternative = exports.Rule = exports.NonTerminal = exports.AbstractProduction = void 0;
var map_1 = __importDefault(require("lodash/map"));
var forEach_1 = __importDefault(require("lodash/forEach"));
var isString_1 = __importDefault(require("lodash/isString"));
var isRegExp_1 = __importDefault(require("lodash/isRegExp"));
var pickBy_1 = __importDefault(require("lodash/pickBy"));
var assign_1 = __importDefault(require("lodash/assign"));
// TODO: duplicated code to avoid extracting another sub-package -- how to avoid?
function tokenLabel(tokType) {
    if (hasTokenLabel(tokType)) {
        return tokType.LABEL;
    }
    else {
        return tokType.name;
    }
}
// TODO: duplicated code to avoid extracting another sub-package -- how to avoid?
function hasTokenLabel(obj) {
    return (0, isString_1.default)(obj.LABEL) && obj.LABEL !== "";
}
var AbstractProduction = /** @class */ (function () {
    function AbstractProduction(_definition) {
        this._definition = _definition;
    }
    Object.defineProperty(AbstractProduction.prototype, "definition", {
        get: function () {
            return this._definition;
        },
        set: function (value) {
            this._definition = value;
        },
        enumerable: false,
        configurable: true
    });
    AbstractProduction.prototype.accept = function (visitor) {
        visitor.visit(this);
        (0, forEach_1.default)(this.definition, function (prod) {
            prod.accept(visitor);
        });
    };
    return AbstractProduction;
}());
exports.AbstractProduction = AbstractProduction;
var NonTerminal = /** @class */ (function (_super) {
    __extends(NonTerminal, _super);
    function NonTerminal(options) {
        var _this = _super.call(this, []) || this;
        _this.idx = 1;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    Object.defineProperty(NonTerminal.prototype, "definition", {
        get: function () {
            if (this.referencedRule !== undefined) {
                return this.referencedRule.definition;
            }
            return [];
        },
        set: function (definition) {
            // immutable
        },
        enumerable: false,
        configurable: true
    });
    NonTerminal.prototype.accept = function (visitor) {
        visitor.visit(this);
        // don't visit children of a reference, we will get cyclic infinite loops if we do so
    };
    return NonTerminal;
}(AbstractProduction));
exports.NonTerminal = NonTerminal;
var Rule = /** @class */ (function (_super) {
    __extends(Rule, _super);
    function Rule(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.orgText = "";
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return Rule;
}(AbstractProduction));
exports.Rule = Rule;
var Alternative = /** @class */ (function (_super) {
    __extends(Alternative, _super);
    function Alternative(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.ignoreAmbiguities = false;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return Alternative;
}(AbstractProduction));
exports.Alternative = Alternative;
var Option = /** @class */ (function (_super) {
    __extends(Option, _super);
    function Option(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.idx = 1;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return Option;
}(AbstractProduction));
exports.Option = Option;
var RepetitionMandatory = /** @class */ (function (_super) {
    __extends(RepetitionMandatory, _super);
    function RepetitionMandatory(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.idx = 1;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return RepetitionMandatory;
}(AbstractProduction));
exports.RepetitionMandatory = RepetitionMandatory;
var RepetitionMandatoryWithSeparator = /** @class */ (function (_super) {
    __extends(RepetitionMandatoryWithSeparator, _super);
    function RepetitionMandatoryWithSeparator(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.idx = 1;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return RepetitionMandatoryWithSeparator;
}(AbstractProduction));
exports.RepetitionMandatoryWithSeparator = RepetitionMandatoryWithSeparator;
var Repetition = /** @class */ (function (_super) {
    __extends(Repetition, _super);
    function Repetition(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.idx = 1;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return Repetition;
}(AbstractProduction));
exports.Repetition = Repetition;
var RepetitionWithSeparator = /** @class */ (function (_super) {
    __extends(RepetitionWithSeparator, _super);
    function RepetitionWithSeparator(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.idx = 1;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    return RepetitionWithSeparator;
}(AbstractProduction));
exports.RepetitionWithSeparator = RepetitionWithSeparator;
var Alternation = /** @class */ (function (_super) {
    __extends(Alternation, _super);
    function Alternation(options) {
        var _this = _super.call(this, options.definition) || this;
        _this.idx = 1;
        _this.ignoreAmbiguities = false;
        _this.hasPredicates = false;
        (0, assign_1.default)(_this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
        return _this;
    }
    Object.defineProperty(Alternation.prototype, "definition", {
        get: function () {
            return this._definition;
        },
        set: function (value) {
            this._definition = value;
        },
        enumerable: false,
        configurable: true
    });
    return Alternation;
}(AbstractProduction));
exports.Alternation = Alternation;
var Terminal = /** @class */ (function () {
    function Terminal(options) {
        this.idx = 1;
        (0, assign_1.default)(this, (0, pickBy_1.default)(options, function (v) { return v !== undefined; }));
    }
    Terminal.prototype.accept = function (visitor) {
        visitor.visit(this);
    };
    return Terminal;
}());
exports.Terminal = Terminal;
function serializeGrammar(topRules) {
    return (0, map_1.default)(topRules, serializeProduction);
}
exports.serializeGrammar = serializeGrammar;
function serializeProduction(node) {
    function convertDefinition(definition) {
        return (0, map_1.default)(definition, serializeProduction);
    }
    /* istanbul ignore else */
    if (node instanceof NonTerminal) {
        var serializedNonTerminal = {
            type: "NonTerminal",
            name: node.nonTerminalName,
            idx: node.idx
        };
        if ((0, isString_1.default)(node.label)) {
            serializedNonTerminal.label = node.label;
        }
        return serializedNonTerminal;
    }
    else if (node instanceof Alternative) {
        return {
            type: "Alternative",
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof Option) {
        return {
            type: "Option",
            idx: node.idx,
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof RepetitionMandatory) {
        return {
            type: "RepetitionMandatory",
            idx: node.idx,
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof RepetitionMandatoryWithSeparator) {
        return {
            type: "RepetitionMandatoryWithSeparator",
            idx: node.idx,
            separator: (serializeProduction(new Terminal({ terminalType: node.separator }))),
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof RepetitionWithSeparator) {
        return {
            type: "RepetitionWithSeparator",
            idx: node.idx,
            separator: (serializeProduction(new Terminal({ terminalType: node.separator }))),
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof Repetition) {
        return {
            type: "Repetition",
            idx: node.idx,
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof Alternation) {
        return {
            type: "Alternation",
            idx: node.idx,
            definition: convertDefinition(node.definition)
        };
    }
    else if (node instanceof Terminal) {
        var serializedTerminal = {
            type: "Terminal",
            name: node.terminalType.name,
            label: tokenLabel(node.terminalType),
            idx: node.idx
        };
        if ((0, isString_1.default)(node.label)) {
            serializedTerminal.terminalLabel = node.label;
        }
        var pattern = node.terminalType.PATTERN;
        if (node.terminalType.PATTERN) {
            serializedTerminal.pattern = (0, isRegExp_1.default)(pattern)
                ? pattern.source
                : pattern;
        }
        return serializedTerminal;
    }
    else if (node instanceof Rule) {
        return {
            type: "Rule",
            name: node.name,
            orgText: node.orgText,
            definition: convertDefinition(node.definition)
        };
    }
    else {
        throw Error("non exhaustive match");
    }
}
exports.serializeProduction = serializeProduction;
//# sourceMappingURL=model.js.map